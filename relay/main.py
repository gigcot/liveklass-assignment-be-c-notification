import os
import time
import uuid
import logging
import random

import mysql.connector
import redis

from notification_client.api_client import ApiClient
from notification_client.configuration import Configuration
from notification_client.api.internal_notification_controller_api import InternalNotificationControllerApi
from notification_client.models.failed_callback_request import FailedCallbackRequest

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "database": os.getenv("DB_NAME", "notification"),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", "root"),
}
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
WEB_BASE_URL = os.getenv("WEB_BASE_URL", "http://localhost:8080")
QUEUE_KEY = "notification:outbox:queue"
BATCH_SIZE = 100
POLL_INTERVAL = 1.0
STALE_THRESHOLD_MINUTES = 5
STALE_CHECK_INTERVAL = 60  # seconds

LPUSH_MAX_RETRIES = 3
LPUSH_BASE_DELAY = 1.0  # seconds


def to_uuid_str(value) -> str:
    if isinstance(value, (bytes, bytearray)):
        return str(uuid.UUID(bytes=bytes(value)))
    return str(value)


FETCH_PENDING_QUERY = """
    SELECT id, notification_id, payload
    FROM outbox_event
    WHERE status = 'PENDING' AND (scheduled_at IS NULL OR scheduled_at <= NOW())
    ORDER BY created_at
    LIMIT %s
    FOR UPDATE SKIP LOCKED
"""

UPDATE_RELAYED_QUERY = "UPDATE outbox_event SET status = 'RELAYED' WHERE id = %s"


def lpush_with_retry(r: redis.Redis, payload_json: str) -> bool:
    """lpush 실패 시 exponential backoff 재시도. 모두 실패하면 False 반환."""
    for attempt in range(LPUSH_MAX_RETRIES):
        try:
            r.lpush(QUEUE_KEY, payload_json)
            return True
        except Exception:
            if attempt == LPUSH_MAX_RETRIES - 1:
                log.exception("lpush failed after %d attempts", LPUSH_MAX_RETRIES)
                return False
            delay = LPUSH_BASE_DELAY * (2 ** attempt) + random.uniform(0, 0.1)
            log.warning("lpush attempt %d failed, retrying in %.2fs", attempt + 1, delay)
            time.sleep(delay)
    return False


FETCH_STALE_QUERY = """
    SELECT BIN_TO_UUID(id) AS id
    FROM user_notification
    WHERE send_status IN ('PENDING', 'QUEUED', 'SENDING')
      AND updated_at < DATE_SUB(NOW(), INTERVAL %s MINUTE)
    LIMIT 100
"""


def recover_stale(db_conn, api: InternalNotificationControllerApi):
    """일정 시간 이상 처리 중 상태에 머문 알림을 FAILED로 전환한다."""
    cursor = db_conn.cursor(dictionary=True)
    try:
        cursor.execute(FETCH_STALE_QUERY, (STALE_THRESHOLD_MINUTES,))
        rows = cursor.fetchall()
        for row in rows:
            notification_id = row["id"]
            try:
                api.mark_failed(notification_id, FailedCallbackRequest(reason="STALE_TIMEOUT"))
                log.info("Recovered stale notification=%s", notification_id)
            except Exception:
                log.exception("Failed to recover notification=%s", notification_id)
    finally:
        cursor.close()


def relay_once(db_conn, r: redis.Redis, api: InternalNotificationControllerApi):
    cursor = db_conn.cursor(dictionary=True)
    try:
        cursor.execute(FETCH_PENDING_QUERY, (BATCH_SIZE,))
        rows = cursor.fetchall()

        if not rows:
            db_conn.commit()
            return

        for row in rows:
            outbox_id_bytes = row["id"]
            notification_id = to_uuid_str(row["notification_id"])
            payload_json = row["payload"]

            cursor.execute(UPDATE_RELAYED_QUERY, (outbox_id_bytes,))

            if not lpush_with_retry(r, payload_json):
                # lpush 최종 실패 → 이 row는 rollback 대상에 포함
                # 다음 폴링에서 PENDING으로 재시도
                raise RuntimeError(f"lpush failed for notification_id={notification_id}")

            db_conn.commit()

            api.mark_queued(notification_id)
            log.info("Relayed: notification_id=%s", notification_id)

    except Exception:
        db_conn.rollback()
        log.exception("Relay error")
    finally:
        cursor.close()


def main():
    r = redis.Redis(host=REDIS_HOST, port=6379, decode_responses=True)
    config = Configuration(host=WEB_BASE_URL)
    api_client = ApiClient(config)
    api = InternalNotificationControllerApi(api_client)

    while True:
        try:
            with mysql.connector.connect(**DB_CONFIG) as db_conn:
                log.info("Connected to DB. Running startup stale recovery...")
                recover_stale(db_conn, api)

                log.info("Starting relay loop...")
                last_stale_check = time.monotonic()
                while True:
                    relay_once(db_conn, r, api)

                    now = time.monotonic()
                    if now - last_stale_check >= STALE_CHECK_INTERVAL:
                        recover_stale(db_conn, api)
                        last_stale_check = now

                    time.sleep(POLL_INTERVAL)
        except Exception:
            log.exception("Connection error. Retrying in 5s...")
            time.sleep(5)


if __name__ == "__main__":
    main()
