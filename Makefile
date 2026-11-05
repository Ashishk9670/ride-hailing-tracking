.PHONY: demo demo-down docs-media build test unit integration verify up down run e2e e2e-install coverage clean

build:        ## compile + package without tests
	mvn -q -DskipTests package

unit:         ## unit tests only (no Docker needed)
	mvn -q test

integration:  ## integration tests (Testcontainers, needs Docker)
	mvn -q verify

verify: integration

coverage:     ## unit+IT run, report in target/site/jacoco/index.html
	mvn -q verify

up:           ## start Kafka, Redis, PostGIS
	docker compose up -d --wait

down:
	docker compose down -v

run:
	mvn spring-boot:run

e2e-install:
	cd e2e && npm install && npx playwright install --with-deps chromium

e2e:          ## requires `make up` and the app running (make run)
	cd e2e && npx playwright test

clean:
	mvn -q clean

demo:         ## stack up + app + driver simulator running (open http://localhost:8080)
	./scripts/demo.sh

demo-down:    ## stop the demo app and the compose stack
	-@[ -f target/demo-app.pid ] && kill $$(cat target/demo-app.pid) && rm -f target/demo-app.pid
	docker compose down -v

docs-media:   ## re-capture README screenshots into docs/img (needs `make demo`; GIF is built in CI, see docs-media workflow)
	cd e2e && npx playwright test -c playwright.demo.config.ts
