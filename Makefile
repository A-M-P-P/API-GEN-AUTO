.PHONY: install run-backend run-frontend test build docker

install:
	cd backend && mvn -q -DskipTests package
	cd frontend && npm install

run-backend:
	cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

run-frontend:
	cd frontend && npm start

test:
	cd backend && mvn test

build:
	cd backend && mvn -q -DskipTests package
	cd frontend && npm run build

docker:
	docker compose up --build
