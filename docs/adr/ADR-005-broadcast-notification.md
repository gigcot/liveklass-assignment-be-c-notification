# ADR-005: 공지 알림 분리 (BroadcastNotification)

## 컨텍스트

공지사항처럼 전체 또는 특정 그룹에 동시에 발송하는 알림이 필요하다.
기존 Notification(UserNotification)은 특정 유저를 대상으로 하며, 읽음 여부를 객체 필드(`readAt`)로 관리한다.
전체 발송 알림을 같은 구조로 처리하면 유저마다 읽음 상태가 달라야 하므로 객체 단위로 `readAt`을 관리할 수 없다.

## 대안

**fan-out: 유저 수만큼 UserNotification 생성**

발송 시점에 대상 유저 목록을 조회해서 UserNotification을 유저 수만큼 생성한다.
읽음 처리는 기존 구조를 그대로 사용할 수 있지만, 수십만 유저 대상 발송 시 대량 INSERT 부하가 발생한다.

## 결정

BroadcastNotification을 별도 클래스로 분리한다.

- **UserNotification**: 특정 유저 대상, `readAt` 필드로 읽음 관리
- **BroadcastNotification**: 전체/그룹 대상, 읽음은 `BroadcastReadLog(notificationId, userId, readAt)` 별도 테이블로 관리

발송 흐름(큐 적재 → 워커 발송 → 상태 전이 → 재시도)은 두 타입 모두 동일하다.
워커는 큐에서 꺼낸 메시지의 타입을 구분해서 처리한다.

## 결과

- 대량 발송 시 fan-out 부하 없음
- 읽음 처리 방식이 타입에 따라 명확하게 분리됨
- 두 클래스에 발송 상태 전이 로직이 각각 존재하는 트레이드오프
