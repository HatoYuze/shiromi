package com.github.hatoyuze.luogu.gui.platform

import platform.UIKit.UIDevice

/** iOS 设备指纹：`identifierForVendor`（应用供应商维度的稳定 UUID）。 */
actual fun deviceKeySeed(): String =
    UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "shiromi-ios"
