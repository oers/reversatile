# Builds the same droidzebra-jni sources as project/src/main/jni/Android.mk,
# but for the host platform (glibc, system JDK's JNI headers) instead of the
# Android NDK (Bionic). Lets a plain JVM unit test load the native engine
# directly via droidzebra-jni.c's real JNI entry points - no Android
# emulator/device involved, so it can run on a genuine arm64 CPU (a
# GitHub-hosted arm64 Linux runner) without needing hardware-accelerated
# virtualization, which those runners don't have for the Android emulator.
#
# Keeps the same preprocessor defines as Android.mk (notably -DANDROID) so
# this compiles the exact same code paths the real app ships, not a
# separately-diverging "desktop zebra" build.

JNI_SRC_DIR := ../src/main/jni
BUILD_DIR   := build

ifndef JAVA_HOME
$(error JAVA_HOME must be set to a JDK that provides jni.h)
endif

HOST_OS := $(shell uname -s | tr A-Z a-z)
ifeq ($(HOST_OS),darwin)
JNI_PLATFORM_DIR := darwin
SHARED_EXT := dylib
SHARED_FLAG := -dynamiclib
else
JNI_PLATFORM_DIR := linux
SHARED_EXT := so
SHARED_FLAG := -shared
endif

SRCS := \
	$(JNI_SRC_DIR)/droidzebra-jni.c \
	$(JNI_SRC_DIR)/droidzebra-autop.c \
	$(JNI_SRC_DIR)/droidzebra-display.c \
	$(JNI_SRC_DIR)/droidzebra-json.c \
	$(JNI_SRC_DIR)/droidzebra-msg.c \
	$(JNI_SRC_DIR)/zebra/bitbcnt.c \
	$(JNI_SRC_DIR)/zebra/bitbmob.c \
	$(JNI_SRC_DIR)/zebra/bitboard.c \
	$(JNI_SRC_DIR)/zebra/bitbtest.c \
	$(JNI_SRC_DIR)/zebra/cntflip.c \
	$(JNI_SRC_DIR)/zebra/counter.c \
	$(JNI_SRC_DIR)/zebra/doflip.c \
	$(JNI_SRC_DIR)/zebra/end.c \
	$(JNI_SRC_DIR)/zebra/epcstat.c \
	$(JNI_SRC_DIR)/zebra/eval.c \
	$(JNI_SRC_DIR)/zebra/game.c \
	$(JNI_SRC_DIR)/zebra/getcoeff.c \
	$(JNI_SRC_DIR)/zebra/globals.c \
	$(JNI_SRC_DIR)/zebra/hash.c \
	$(JNI_SRC_DIR)/zebra/learn.c \
	$(JNI_SRC_DIR)/zebra/midgame.c \
	$(JNI_SRC_DIR)/zebra/moves.c \
	$(JNI_SRC_DIR)/zebra/myrandom.c \
	$(JNI_SRC_DIR)/zebra/opname.c \
	$(JNI_SRC_DIR)/zebra/osfbook.c \
	$(JNI_SRC_DIR)/zebra/patterns.c \
	$(JNI_SRC_DIR)/zebra/pcstat.c \
	$(JNI_SRC_DIR)/zebra/probcut.c \
	$(JNI_SRC_DIR)/zebra/safemem.c \
	$(JNI_SRC_DIR)/zebra/search.c \
	$(JNI_SRC_DIR)/zebra/stable.c \
	$(JNI_SRC_DIR)/zebra/thordb.c \
	$(JNI_SRC_DIR)/zebra/timer.c \
	$(JNI_SRC_DIR)/zebra/unflip.c \
	$(JNI_SRC_DIR)/zebra/threads.c

DEFS := -DZLIB_STATIC -D__linux__ -DANDROID
CFLAGS := -O2 -fPIC -Wall $(DEFS) \
	-I$(JNI_SRC_DIR) \
	-I$(JAVA_HOME)/include \
	-I$(JAVA_HOME)/include/$(JNI_PLATFORM_DIR)
LDLIBS := -lm -lz -lpthread

TARGET := $(BUILD_DIR)/libdroidzebra.$(SHARED_EXT)

.PHONY: all clean
all: $(TARGET)

$(TARGET): $(SRCS)
	mkdir -p $(BUILD_DIR)
	$(CC) $(SHARED_FLAG) $(CFLAGS) -o $@ $(SRCS) $(LDLIBS)

clean:
	rm -rf $(BUILD_DIR)
