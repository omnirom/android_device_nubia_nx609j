# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This file is the build configuration for a full Android
# build for grouper hardware. This cleanly combines a set of
# device-specific aspects (drivers) with a device-agnostic
# product configuration (apps).
#

# Sample: This is where we'd set a backup provider if we had one
# $(call inherit-product, device/sample/products/backup_overlay.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)

# Get the prebuilt list of APNs
$(call inherit-product, vendor/omni/config/gsm.mk)

# Inherit from the common Open Source product configuration
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_base_telephony.mk)

# must be before including omni part
#treble
$(call inherit-product, build/make/target/product/treble_common_64.mk)

TARGET_BOOTANIMATION_SIZE := 1080p

# Inherit from our custom product configuration
$(call inherit-product, vendor/omni/config/common.mk)

# Inherit from hardware-specific part of the product configuration
$(call inherit-product, device/nubia/nx609j/device.mk)

ALLOW_MISSING_DEPENDENCIES := true

PRODUCT_PACKAGES += \
    android.hardware.biometrics.fingerprint@2.1-service

# Discard inherited values and use our own instead.
PRODUCT_NAME := omni_nx609j
PRODUCT_DEVICE := nx609j
PRODUCT_BRAND := Nubia
PRODUCT_MANUFACTURER := Nubia
PRODUCT_MODEL := Nubia Red Magic

PRODUCT_BUILD_PROP_OVERRIDES += TARGET_DEVICE=nx609j PRODUCT_NAME=NUBIA_NX609J

PRODUCT_BUILD_PROP_OVERRIDES += \
    BUILD_FINGERPRINT=nubia/NX609J/NX609J:8.1.0/OPM1.171019.011/eng.nubia.20180614.113014:user/release-keys \
    PRIVATE_BUILD_DESC="NX609J-user 8.1.0 OPM1.171019.011 eng.nubia.20180614.113014 release-keys"

TARGET_VENDOR := nubia
