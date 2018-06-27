CUSTOM_PRODUCT_VERITY_SIGNING_KEY := build/target/product/security/verity

$(INSTALLED_RECOVERYIMAGE_TARGET): $(MKBOOTIMG) $(MINIGZIP) \
		$(recovery_uncompressed_ramdisk) \
		$(recovery_kernel)
	@echo ----- Compressing recovery ramdisk ------
	$(MKBOOTFS) $(TARGET_RECOVERY_ROOT_OUT) | $(MINIGZIP) > $(recovery_ramdisk)
	@echo ----- Making recovery image ------
	$(MKBOOTIMG) $(INTERNAL_RECOVERYIMAGE_ARGS) $(BOARD_MKBOOTIMG_ARGS) --output $@
	@echo ----- Signing recovery image -------- $@
	$(BOOT_SIGNER) /recovery $@ $(CUSTOM_PRODUCT_VERITY_SIGNING_KEY).pk8 $(CUSTOM_PRODUCT_VERITY_SIGNING_KEY).x509.pem $@
	@echo ----- Made recovery image -------- $@
	$(hide) $(call assert-max-image-size,$@,$(BOARD_RECOVERYIMAGE_PARTITION_SIZE),raw)

$(INSTALLED_BOOTIMAGE_TARGET): $(MKBOOTIMG) $(INTERNAL_BOOTIMAGE_FILES) $(BOOT_SIGNER)
	$(call pretty,"Target boot image: $@")
	$(hide) $(MKBOOTIMG) $(INTERNAL_BOOTIMAGE_ARGS) $(INTERNAL_MKBOOTIMG_VERSION_ARGS) $(BOARD_MKBOOTIMG_ARGS) --output $@
	$(BOOT_SIGNER) /boot $@ $(CUSTOM_PRODUCT_VERITY_SIGNING_KEY).pk8 $(CUSTOM_PRODUCT_VERITY_SIGNING_KEY).x509.pem $@
	$(hide) $(call assert-max-image-size,$@,$(BOARD_BOOTIMAGE_PARTITION_SIZE))
