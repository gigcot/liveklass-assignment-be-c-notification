import asyncio
import json
import logging
import os

import redis.asyncio as aioredis

from senders import email_sender, in_app_sender

# make generate-client 실행 후 생성됨
from notification_client.api_client import ApiClient
from notification_client.configuration import Configuration
from notification_client.api.internal_notification_controller_api import InternalNotificationControllerApi
from notification_client.models.failed_callback_request import FailedCallbackRequest

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
WEB_BASE_URL = os.getenv("WEB_BASE_URL", "http://localhost:8080")
CONCURRENCY = int(os.getenv("WORKER_CONCURRENCY", "1"))
QUEUE_KEY = "notification:outbox:queue"
LOCK_KEY_PREFIX = "notification:lock:"
LOCK_TTL = 60   # seconds (최대 처리시간 ~35s 고려)
RETRY_BASE_DELAY = 4.0   # seconds: 4s → 8s → 16s (총 28s)
RETRY_MAX_ATTEMPTS = 4   # 서버 버그 시 무한루프 방지용 상한 (서버 max=3)

SENDERS = {
    "EMAIL": email_sender,
    "IN_APP": in_app_sender,
}


async def dispatch(payload: dict) -> bool:
    channel = payload.get("channel")
    sender = SENDERS.get(channel)
    if sender is None:
        log.error("Unknown channel: %s", channel)
        return False
    return await sender.send(payload)


async def process(worker_name: str, payload_json: str, r: aioredis.Redis, api: InternalNotificationControllerApi):
    payload = json.loads(payload_json)
    notification_id = payload["notificationId"]

    # 멱등성: 같은 notificationId 중복 처리 방지
    lock_key = f"{LOCK_KEY_PREFIX}{notification_id}"
    acquired = await r.set(lock_key, "1", nx=True, ex=LOCK_TTL)
    if not acquired:
        log.warning("[%s] Duplicate skipped: notification_id=%s", worker_name, notification_id)
        return

    try:
        try:
            await api.claim(notification_id)
        except Exception as e:
            if hasattr(e, 'status') and e.status == 409:
                log.warning("[%s] Claim failed (already processing): notification_id=%s", worker_name, notification_id)
                return
            raise

        attempt = 0
        while True:
            success = await dispatch(payload)

            if success:
                await api.mark_sent(notification_id)
                log.info("[%s] Sent: notification_id=%s", worker_name, notification_id)
                return

            body = FailedCallbackRequest(reason="SEND_FAILED")
            resp = await api.mark_failed(notification_id, body)
            retry = resp["retry"] if isinstance(resp, dict) else resp.retry

            if not retry:
                log.error("[%s] Failed (final): notification_id=%s", worker_name, notification_id)
                return

            attempt += 1
            if attempt >= RETRY_MAX_ATTEMPTS:
                log.error("[%s] Failed (max attempts=%d reached): notification_id=%s",
                          worker_name, RETRY_MAX_ATTEMPTS, notification_id)
                return

            delay = RETRY_BASE_DELAY * (2 ** (attempt - 1))
            log.warning("[%s] Failed (attempt=%d/%d, retry in %.1fs): notification_id=%s",
                        worker_name, attempt, RETRY_MAX_ATTEMPTS, delay, notification_id)
            await asyncio.sleep(delay)
    finally:
        await r.delete(lock_key)


async def worker_loop(worker_id: int, r: aioredis.Redis, api: InternalNotificationControllerApi):
    worker_name = f"발송워커-{worker_id}"
    log.info("%s started", worker_name)

    while True:
        try:
            result = await r.brpop(QUEUE_KEY, timeout=0)
            if result:
                _, payload_json = result
                await process(worker_name, payload_json, r, api)
        except Exception:
            log.exception("%s error", worker_name)


class WorkerFactory:
    def __init__(self, r: aioredis.Redis, api: InternalNotificationControllerApi):
        self.r = r
        self.api = api

    def create(self, worker_id: int):
        return worker_loop(worker_id, self.r, self.api)


async def main():
    r = aioredis.Redis(host=REDIS_HOST, port=6379, decode_responses=True)
    config = Configuration(host=WEB_BASE_URL)
    async with ApiClient(config) as api_client:
        api = InternalNotificationControllerApi(api_client)
        factory = WorkerFactory(r, api)
        workers = [factory.create(i) for i in range(CONCURRENCY)]
        log.info("Starting %d worker(s)...", CONCURRENCY)
        await asyncio.gather(*workers)


if __name__ == "__main__":
    asyncio.run(main())
