IMAGE 		   		?= android-builder:local
DOCKERFILE      	?= whatsapp-chat.Dockerfile
GRADLE_VERSION 		?= 9.5.0
ANDROID_PLATFORM 	?= 37.0
BUILD_TOOLS 		?= 36.0.0

DOCKER_RUN = docker run --rm \
	-v "$(CURDIR)":/whatsapp-chat-converter \
	-v gradle-cache:/root/.gradle

.PHONY: debug clean release

image:
	docker build -f $(DOCKERFILE) \
		--build-arg GRADLE_VERSION=$(GRADLE_VERSION) \
		--build-arg ANDROID_PLATFORM=$(ANDROID_PLATFORM) \
		--build-arg BUILD_TOOLS=$(BUILD_TOOLS) \
		-t $(IMAGE) .

# RESULT : app/build/outputs/apk/debug

debug: image
	$(DOCKER_RUN) $(IMAGE) gradle assembleDebug --no-daemon --stacktrace

test: image
	$(DOCKER_RUN) $(IMAGE) gradle testDebugUnitTest --no-daemon --stacktrace

lint: image
	$(DOCKER_RUN) $(IMAGE) gradle lintDebug --no-daemon --stacktrace

ci: image
	$(DOCKER_RUN) $(IMAGE) gradle lintDebug testDebugUnitTest assembleDebug --no-daemon --stacktrace

# RESULT: app/build/outputs/apk/release

release: image
	@test -n "$(KEYSTORE)" || (echo "KEYSTORE belum diisi" && exit 1)
	$(DOCKER_RUN) \
		-v "$(KEYSTORE)":/keystore/release.jks:ro \
		-e KEYSTORE=/keystore/release.jks \
		-e KEYSTORE_PASSWORD \
		-e KEY_ALIAS \
		-e KEY_PASSWORD \
		$(IMAGE) gradle bundleRelease assembleRelease --no-daemon --stacktrace

clean:
	$(DOCKER_RUN) $(IMAGE) gradle clean --no-daemon

shell: image
	docker run --rm -it -v "$(CURDIR)":/whatsapp-chat-converter -v gradle-cache:/root/.gradle $(IMAGE) bash

