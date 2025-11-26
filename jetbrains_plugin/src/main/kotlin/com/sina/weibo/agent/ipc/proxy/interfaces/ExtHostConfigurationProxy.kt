// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.ipc.proxy.interfaces

interface ExtHostConfigurationProxy {
    fun initializeConfiguration(configModel: Map<String, Any?>)
    
    fun acceptConfigurationChanged(data: Map<String, Any?>, change: Map<String, Any?>)
    
    fun getConfiguration(key: String, section: String?, scopeToLanguage: Boolean): Any?
} 