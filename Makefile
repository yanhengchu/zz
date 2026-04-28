# -------------------------------------
# make
# -------------------------------------

version:
	make -version

# -------------------------------------
# global
# -------------------------------------

PACKAGE_NAME := cc.ai.music

# -------------------------------------
# adb
# -------------------------------------

devices:
	adb devices
top:
	adb shell dumpsys activity top | grep ACTIVITY || true
size:
	adb shell wm size
layoutOn:
	adb shell setprop debug.layout true
	adb shell service call activity 1599295570
layoutOff:
	adb shell setprop debug.layout false
	adb shell service call activity 1599295570

touchOn:
	adb shell settings put system show_touches 1
	adb shell settings put system pointer_location 1
touchOff:
	adb shell settings put system show_touches 0
	adb shell settings put system pointer_location 0
profileOn:
	adb shell setprop debug.hwui.profile visual_bars
	adb shell stop
	adb shell start
profileOff:
	adb shell setprop debug.hwui.profile false
	adb shell stop
	adb shell start

ps:
	adb shell ps | grep $(PACKAGE_NAME) || true
stop:
	adb shell am force-stop $(PACKAGE_NAME)
clear:
	adb shell pm clear $(PACKAGE_NAME)
log:
	adb logcat -v time | grep --line-buffered $(PACKAGE_NAME) || true

# -------------------------------------
# git
# -------------------------------------

gs:
	git log -1 --oneline
	git status
ga:
	git add .
gam: ga
	git commit --amend --no-edit

ginit:
# 新建一个没有历史的孤儿分支
	git checkout --orphan temp
# 添加所有文件
	git add -A
# 全量提交一次
	git commit -m "init"
# 删除旧 main 分支
	git branch -D main
# 把新分支改名为 main
	git branch -m main
# 强制推送覆盖远程
	git push -f origin main

# -------------------------------------
# gradlew (跨平台)
# -------------------------------------

ifeq ($(OS),Windows_NT)
    GRADLEW = .\gradlew
else
    GRADLEW = ./gradlew
endif

clean:
	$(GRADLEW) clean

debug:
	$(GRADLEW) :app:assembleDebug

release:
	$(GRADLEW) :app:assembleRelease

install-debug:
	$(GRADLEW) :app:installDebug

install-release:
	adb install -r app/build/outputs/apk/release/app-release-unsigned.apk

launch:
	adb shell am start -n $(PACKAGE_NAME)/$(PACKAGE_NAME).feature.home.MainActivity

run: debug install-debug launch

# -------------------------------------
# emulator
# -------------------------------------

emu:
	emulator -list-avds
emu1:
	emulator @Resizable_Experimental
