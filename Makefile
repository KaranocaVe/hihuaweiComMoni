.PHONY: up down logs test build clean

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f --tail=200

test:
	cd backend && mvn test
	cd frontend && npm run build

build:
	docker compose build

clean:
	docker compose down --remove-orphans
