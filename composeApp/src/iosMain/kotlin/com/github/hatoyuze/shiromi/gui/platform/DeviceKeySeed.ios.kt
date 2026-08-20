// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import platform.UIKit.UIDevice

/** iOS 设备指纹：`identifierForVendor`（应用供应商维度的稳定 UUID）。 */
actual fun deviceKeySeed(): String =
    UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "shiromi-ios"
