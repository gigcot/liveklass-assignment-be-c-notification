# ADR-004: 중복 발송 방지

## 컨텍스트

중복 발송이 발생할 수 있는 시나리오는 세 가지다.

1. 같은 이벤트로 API 접수가 두 번 들어오는 경우
2. 발송 후 SENT 업데이트 전에 서버가 죽어서 복구 시 같은 알림을 다시 처리하는 경우
3. MQ at-least-once 특성으로 같은 메시지가 워커에게 두 번 배달되는 경우

## 결정

**시나리오 1 — `(userId, eventId, channel)` unique constraint**

같은 이벤트에 대해 동일 수신자 + 동일 채널로 알림이 두 번 접수되는 것을 DB 제약으로 막는다.
접수 시점에 SELECT로 확인하지 않고, INSERT 실패(constraint violation)로 처리한다.
SELECT 후 INSERT 방식은 동시 요청 시 둘 다 통과하는 race condition이 생긴다.


**시나리오 2, 3 — SELECT FOR UPDATE로 row 락**

워커가 알림을 처리할 때 해당 row에 SELECT FOR UPDATE로 락을 건다.
락을 잡은 워커만 발송을 진행하고, 발송 결과에 따라 SENT/FAILED로 업데이트 후 커밋하면서 락을 해제한다.
같은 알림에 접근하려는 다른 워커는 락이 해제될 때까지 대기하고, 이미 SENT면 skip한다.
row 레벨 락이므로 같은 테이블의 다른 알림 처리에는 영향을 주지 않는다.

## 결과

- API 접수 중복은 DB constraint로 차단
- 워커 중복 처리는 row 락으로 차단
- 발송 채널 호출이 락 잡은 상태에서 일어나지만, 해당 row 하나에만 영향이므로 허용 가능한 트레이드오프
