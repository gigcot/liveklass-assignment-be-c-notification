.PHONY: generate-client up down

# Spring Boot에서 OpenAPI spec 추출 후 Python 클라이언트 자동 생성
# 사전 조건: openapi-generator-cli 설치 (brew install openapi-generator)
generate-client:
	./gradlew generateOpenApiDocs
	openapi-generator generate \
		-i docs/openapi.json \
		-g python \
		-o generated/notification_client \
		--package-name notification_client \
		--library asyncio
	cp -r generated/notification_client/notification_client worker/notification_client
	cp -r generated/notification_client/notification_client relay/notification_client

up:
	docker compose up --build

down:
	docker compose down
