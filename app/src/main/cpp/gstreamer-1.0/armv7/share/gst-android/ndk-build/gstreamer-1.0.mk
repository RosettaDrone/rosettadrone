# cerbero - a multi-platform build system for Open Source software
# Copyright (C) 2012 Andoni Morales Alastruey <ylatuya@gmail.com>
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Library General Public
# License as published by the Free Software Foundation; either
# version 2 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Library General Public License for more details.
#
# You should have received a copy of the GNU Library General Public
# License along with this library; if not, write to the
# Free Software Foundation, Inc., 59 Temple Place - Suite 330,
# Boston, MA 02111-1307, USA.

$(call assert-defined, GSTREAMER_ROOT)
$(if $(wildcard $(GSTREAMER_ROOT)),,\
  $(error "The directory GSTREAMER_ROOT=$(GSTREAMER_ROOT) does not exists")\
)


#####################
#  Setup variables  #
#####################

ifndef GSTREAMER_PLUGINS
  $(info "The list of GSTREAMER_PLUGINS is empty")
endif

# Expand home directory (~/)
GSTREAMER_ROOT := $(wildcard $(GSTREAMER_ROOT))

# Path for GStreamer static plugins
ifndef GSTREAMER_STATIC_PLUGINS_PATH
GSTREAMER_STATIC_PLUGINS_PATH := $(GSTREAMER_ROOT)/lib/gstreamer-1.0
endif

# Path for the NDK integration makefiles
ifndef GSTREAMER_NDK_BUILD_PATH
GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/share/gst-android/ndk-build
endif

ifndef GSTREAMER_INCLUDE_FONTS
GSTREAMER_INCLUDE_FONTS := yes
endif

ifndef GSTREAMER_INCLUDE_CA_CERTIFICATES
GSTREAMER_INCLUDE_CA_CERTIFICATES := yes
endif

ifndef GSTREAMER_JAVA_SRC_DIR
GSTREAMER_JAVA_SRC_DIR := src
endif

# Include tools
include $(GSTREAMER_NDK_BUILD_PATH)/tools.mk

# Path for the static GIO modules
G_IO_MODULES_PATH := $(GSTREAMER_ROOT)/lib/gio/modules/static

# Host tools
ifeq ($(HOST_OS),windows)
    SED := $(GSTREAMER_NDK_BUILD_PATH)/tools/windows/sed
    SED_LOCAL := "$(GSTREAMER_NDK_BUILD_PATH)/tools/windows/sed"
    EXE_SUFFIX := .exe
else
    SED := sed
    SED_LOCAL := sed
    EXE_SUFFIX :=
endif

ifndef GSTREAMER_ANDROID_MODULE_NAME
GSTREAMER_ANDROID_MODULE_NAME := gstreamer_android
endif
GSTREAMER_BUILD_DIR           := gst-build-$(TARGET_ARCH_ABI)
GSTREAMER_ANDROID_O           := $(GSTREAMER_BUILD_DIR)/$(GSTREAMER_ANDROID_MODULE_NAME).o
GSTREAMER_ANDROID_SO          := $(GSTREAMER_BUILD_DIR)/lib$(GSTREAMER_ANDROID_MODULE_NAME).so
GSTREAMER_ANDROID_C           := $(GSTREAMER_BUILD_DIR)/$(GSTREAMER_ANDROID_MODULE_NAME).c
GSTREAMER_ANDROID_C_IN        := $(GSTREAMER_NDK_BUILD_PATH)/gstreamer_android-1.0.c.in
GSTREAMER_DEPS                := $(GSTREAMER_EXTRA_DEPS) gstreamer-1.0
GSTREAMER_LD                  := -fuse-ld=gold$(EXE_SUFFIX) -Wl,-soname,lib$(GSTREAMER_ANDROID_MODULE_NAME).so

################################
#  NDK Build Prebuilt library  #
################################

# Declare a prebuilt library module, a shared library including
# gstreamer, its dependencies and all its plugins.
# Since the shared library is not really prebuilt, but will be built
# using the defined rules in this file, we can't use the
# PREBUILT_SHARED_LIBRARY makefiles like explained in the docs,
# as it checks for the existance of the shared library. We therefore
# use a custom gstreamer_prebuilt.mk, which skips this step

include $(CLEAR_VARS)
LOCAL_MODULE            := $(GSTREAMER_ANDROID_MODULE_NAME)
LOCAL_SRC_FILES         := $(GSTREAMER_ANDROID_SO)
LOCAL_BUILD_SCRIPT      := PREBUILT_SHARED_LIBRARY
LOCAL_MODULE_CLASS      := PREBUILT_SHARED_LIBRARY
LOCAL_MAKEFILE          := $(local-makefile)
LOCAL_PREBUILT_PREFIX   := lib
LOCAL_PREBUILT_SUFFIX   := .so
LOCAL_EXPORT_C_INCLUDES := $(subst -I$1, $1, $(call pkg-config-get-includes,$(GSTREAMER_DEPS)))
LOCAL_EXPORT_C_INCLUDES += $(GSTREAMER_ROOT)/include


##################################################################
#   Our custom rules to create a shared libray with gstreamer    #
#   and the requested plugins in GSTREAMER_PLUGINS starts here   #
##################################################################

include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer_prebuilt.mk

fix-deps = \
	$(subst $1,$1 $2,$(GSTREAMER_ANDROID_LIBS))


# Generate list of plugin links (eg: -lcoreelements -lvideoscale)
GSTREAMER_PLUGINS_LIBS       := $(foreach plugin, $(GSTREAMER_PLUGINS), -lgst$(plugin) )

GSTREAMER_PLUGINS_CLASSES    := $(strip \
			$(subst $(GSTREAMER_NDK_BUILD_PATH),, \
			$(foreach plugin,$(GSTREAMER_PLUGINS), \
			$(wildcard $(GSTREAMER_NDK_BUILD_PATH)$(plugin)/*.java))))

GSTREAMER_PLUGINS_WITH_CLASSES := $(strip \
			$(subst $(GSTREAMER_NDK_BUILD_PATH),, \
			$(foreach plugin, $(GSTREAMER_PLUGINS), \
			$(wildcard $(GSTREAMER_NDK_BUILD_PATH)$(plugin)))))

# Generate the plugins' declaration strings
GSTREAMER_PLUGINS_DECLARE    := $(foreach plugin, $(GSTREAMER_PLUGINS), \
			GST_PLUGIN_STATIC_DECLARE($(plugin));)
# Generate the plugins' registration strings
GSTREAMER_PLUGINS_REGISTER   := $(foreach plugin, $(GSTREAMER_PLUGINS), \
			GST_PLUGIN_STATIC_REGISTER($(plugin));)

# Generate list of gio modules
G_IO_MODULES_PATH            := $(foreach path, $(G_IO_MODULES_PATH), -L$(path))
G_IO_MODULES_LIBS            := $(foreach module, $(G_IO_MODULES), -lgio$(module))
G_IO_MODULES_DECLARE         := $(foreach module, $(G_IO_MODULES), \
			GST_G_IO_MODULE_DECLARE($(module));)
G_IO_MODULES_LOAD            := $(foreach module, $(G_IO_MODULES), \
			GST_G_IO_MODULE_LOAD($(module));)

# Get the full list of libraries
# link at least to gstreamer-1.0 in case the plugins list is empty
GSTREAMER_ANDROID_LIBS       := $(call pkg-config-get-libs,$(GSTREAMER_DEPS))
GSTREAMER_ANDROID_LIBS       += $(GSTREAMER_PLUGINS_LIBS) $(G_IO_MODULES_LIBS) -llog -lz
GSTREAMER_ANDROID_WHOLE_AR   := $(call pkg-config-get-libs-no-deps,$(GSTREAMER_DEPS))
# Fix deps for giognutls
GSTREAMER_ANDROID_LIBS       := $(call fix-deps,-lgiognutls, -lhogweed)
GSTREAMER_ANDROID_CFLAGS     := $(call pkg-config-get-includes,$(GSTREAMER_DEPS)) -I$(GSTREAMER_ROOT)/include

# In newer NDK, SYSROOT is replaced by SYSROOT_INC and SYSROOT_LINK, which
# now points to the root directory. But this will probably change in the future from:
# https://android.googlesource.com/platform/ndk/+/fa8c1b4338c1bef2813ecee0ee298e9498a1aaa7
ifdef SYSROOT
    SYSROOT_GST_INC := $(SYSROOT)
    SYSROOT_GST_LINK := $(SYSROOT)
else
    ifdef SYSROOT_INC
        $(call assert-defined, SYSROOT_LINK)
        ifdef SYSROOT_LINK
            SYSROOT_GST_INC := $(SYSROOT_INC)
            SYSROOT_GST_LINK := $(SYSROOT_LINK)
        endif
    else
        SYSROOT_GST_INC := $(NDK_PLATFORMS_ROOT)/$(TARGET_PLATFORM)/arch-$(TARGET_ARCH)
        SYSROOT_GST_LINK := $(SYSROOT_GST_INC)
    endif
endif

# Create the link command
GSTREAMER_ANDROID_CMD        := $(call libtool-link,$(TARGET_CC) $(TARGET_LDFLAGS) -shared --sysroot=$(SYSROOT_GST_LINK) \
	-o $(GSTREAMER_ANDROID_SO) $(GSTREAMER_ANDROID_O) \
	-L$(GSTREAMER_ROOT)/lib -L$(GSTREAMER_STATIC_PLUGINS_PATH) $(G_IO_MODULES_PATH) \
	$(GSTREAMER_ANDROID_LIBS), $(GSTREAMER_LD)) -Wl,-no-undefined $(GSTREAMER_LD)
GSTREAMER_ANDROID_CMD        := $(call libtool-whole-archive,$(GSTREAMER_ANDROID_CMD),$(GSTREAMER_ANDROID_WHOLE_AR))

# This triggers the build of our library using our custom rules
$(GSTREAMER_ANDROID_SO): buildsharedlibrary_$(TARGET_ARCH_ABI)
$(GSTREAMER_ANDROID_SO): copyjavasource_$(TARGET_ARCH_ABI)
ifeq ($(GSTREAMER_INCLUDE_FONTS),yes)
$(GSTREAMER_ANDROID_SO): copyfontsres_$(TARGET_ARCH_ABI)
endif
ifeq ($(GSTREAMER_INCLUDE_CA_CERTIFICATES),yes)
$(GSTREAMER_ANDROID_SO): copycacertificatesres_$(TARGET_ARCH_ABI)
endif

delsharedlib_$(TARGET_ARCH_ABI): PRIV_B_DIR := $(GSTREAMER_BUILD_DIR)
delsharedlib_$(TARGET_ARCH_ABI):
	$(hide)$(call host-rm,$(prebuilt))
	$(hide)$(foreach path,$(wildcard $(PRIV_B_DIR)/sed*), $(call host-rm,$(path)) && ) echo Done rm
$(LOCAL_INSTALLED): delsharedlib_$(TARGET_ARCH_ABI)

# Generates a source file that declares and registers all the required plugins
# about the sed command, android-studio doesn't seem to like line continuation characters when executing shell commands
genstatic_$(TARGET_ARCH_ABI): PRIV_C := $(GSTREAMER_ANDROID_C)
genstatic_$(TARGET_ARCH_ABI): PRIV_B_DIR := $(GSTREAMER_BUILD_DIR)
genstatic_$(TARGET_ARCH_ABI): PRIV_C_IN := $(GSTREAMER_ANDROID_C_IN)
genstatic_$(TARGET_ARCH_ABI): PRIV_P_D := $(GSTREAMER_PLUGINS_DECLARE)
genstatic_$(TARGET_ARCH_ABI): PRIV_P_R := $(GSTREAMER_PLUGINS_REGISTER)
genstatic_$(TARGET_ARCH_ABI): PRIV_G_L := $(G_IO_MODULES_LOAD)
genstatic_$(TARGET_ARCH_ABI): PRIV_G_R := $(G_IO_MODULES_DECLARE)
genstatic_$(TARGET_ARCH_ABI):
	$(hide)$(HOST_ECHO) "GStreamer      : [GEN] => $(PRIV_C)"
	$(hide)$(call host-mkdir,$(PRIV_B_DIR))
	$(hide)$(SED_LOCAL) "s/@PLUGINS_DECLARATION@/$(PRIV_P_D)/g" $(PRIV_C_IN) | $(SED_LOCAL) "s/@PLUGINS_REGISTRATION@/$(PRIV_P_R)/g" | $(SED_LOCAL) "s/@G_IO_MODULES_LOAD@/$(PRIV_G_L)/g" | $(SED_LOCAL) "s/@G_IO_MODULES_DECLARE@/$(PRIV_G_R)/g" > $(PRIV_C)

# Compile the source file
$(GSTREAMER_ANDROID_O): PRIV_C := $(GSTREAMER_ANDROID_C)
$(GSTREAMER_ANDROID_O): PRIV_CC_CMD := $(TARGET_CC) --sysroot=$(SYSROOT_GST_INC) $(SYSROOT_ARCH_INC_ARG) $(TARGET_CFLAGS) \
	-c $(GSTREAMER_ANDROID_C) -Wall -Werror -o $(GSTREAMER_ANDROID_O) $(GSTREAMER_ANDROID_CFLAGS)
$(GSTREAMER_ANDROID_O): PRIV_GST_CFLAGS := $(GSTREAMER_ANDROID_CFLAGS) $(TARGET_CFLAGS)
$(GSTREAMER_ANDROID_O): genstatic_$(TARGET_ARCH_ABI)
	$(hide)$(HOST_ECHO) "GStreamer      : [COMPILE] => $(PRIV_C)"
	$(hide)$(PRIV_CC_CMD)

# Creates a shared library including gstreamer, its plugins and all the dependencies
buildsharedlibrary_$(TARGET_ARCH_ABI): PRIV_CMD := $(GSTREAMER_ANDROID_CMD)
buildsharedlibrary_$(TARGET_ARCH_ABI): PRIV_SO := $(GSTREAMER_ANDROID_SO)
buildsharedlibrary_$(TARGET_ARCH_ABI): $(GSTREAMER_ANDROID_O)
	$(hide)$(HOST_ECHO) "GStreamer      : [LINK] => $(PRIV_SO)"
	$(hide)$(PRIV_CMD)

ifeq ($(GSTREAMER_INCLUDE_FONTS),yes)
GSTREAMER_INCLUDE_FONTS_SUBST :=
else
GSTREAMER_INCLUDE_FONTS_SUBST := //
endif

ifeq ($(GSTREAMER_INCLUDE_CA_CERTIFICATES),yes)
GSTREAMER_INCLUDE_CA_CERTIFICATES_SUBST := 
else
GSTREAMER_INCLUDE_CA_CERTIFICATES_SUBST := //
endif

ifneq (,$(findstring yes,$(GSTREAMER_INCLUDE_FONTS)$(GSTREAMER_INCLUDE_CA_CERTIFICATES)))
GSTREAMER_COPY_FILE_SUBST := 
else
GSTREAMER_COPY_FILE_SUBST := //
endif

# about the sed command, android-studio doesn't seem to like line continuation characters when executing shell commands
copyjavasource_$(TARGET_ARCH_ABI):
	$(hide)$(call host-mkdir,$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer)
	$(hide)$(foreach plugin,$(GSTREAMER_PLUGINS_WITH_CLASSES), \
		$(call host-mkdir,$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer/$(plugin)) && ) echo Done mkdir
	$(hide)$(foreach file,$(GSTREAMER_PLUGINS_CLASSES), \
		$(call host-cp,$(GSTREAMER_NDK_BUILD_PATH)$(file),$(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer/$(file)) && ) echo Done cp
	$(hide)$(SED_LOCAL) "s;@INCLUDE_FONTS@;$(GSTREAMER_INCLUDE_FONTS_SUBST);g" $(GSTREAMER_NDK_BUILD_PATH)/GStreamer.java | $(SED_LOCAL) "s;@INCLUDE_CA_CERTIFICATES@;$(GSTREAMER_INCLUDE_CA_CERTIFICATES_SUBST);g" | $(SED_LOCAL) "s;@INCLUDE_COPY_FILE@;$(GSTREAMER_COPY_FILE_SUBST);g" > $(GSTREAMER_JAVA_SRC_DIR)/org/freedesktop/gstreamer/GStreamer.java

ifndef GSTREAMER_ASSETS_DIR
GSTREAMER_ASSETS_DIR := src/main/assets
endif

copyfontsres_$(TARGET_ARCH_ABI):
	$(hide)$(call host-mkdir,$(GSTREAMER_ASSETS_DIR)/fontconfig)
	$(hide)$(call host-mkdir,$(GSTREAMER_ASSETS_DIR)/fontconfig/fonts/truetype/)
	$(hide)$(call host-cp,$(GSTREAMER_NDK_BUILD_PATH)/fontconfig/fonts.conf,$(GSTREAMER_ASSETS_DIR)/fontconfig)
	$(hide)$(call host-cp,$(GSTREAMER_NDK_BUILD_PATH)/fontconfig/fonts/Ubuntu-R.ttf,$(GSTREAMER_ASSETS_DIR)/fontconfig/fonts/truetype)
copycacertificatesres_$(TARGET_ARCH_ABI):
	$(hide)$(call host-mkdir,$(GSTREAMER_ASSETS_DIR)/ssl/certs)
	$(hide)$(call host-cp,$(GSTREAMER_ROOT)/etc/ssl/certs/ca-certificates.crt,$(GSTREAMER_ASSETS_DIR)/ssl/certs)

