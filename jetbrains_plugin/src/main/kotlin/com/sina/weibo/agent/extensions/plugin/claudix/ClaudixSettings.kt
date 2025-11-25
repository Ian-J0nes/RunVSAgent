// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.claudix

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Claudix Settings State
 *
 * This matches the configuration in Claudix's package.json:
 * - claudix.selectedModel: The AI model for Claude Code
 * - claudix.environmentVariables: Environment variables to set when launching Claude
 */
data class ClaudixSettingsState(
    var selectedModel: String = "default",
    var environmentVariables: MutableList<EnvironmentVariable> = mutableListOf()
)

/**
 * Environment Variable configuration
 */
data class EnvironmentVariable(
    var name: String = "",
    var value: String = ""
)

/**
 * Claudix Settings Service
 *
 * Persists Claudix-specific configuration at the application level.
 * These settings correspond to VSCode's claudix.* configuration entries.
 */
@Service(Service.Level.APP)
@State(
    name = "ClaudixSettings",
    storages = [Storage("claudix-settings.xml")]
)
class ClaudixSettings : PersistentStateComponent<ClaudixSettingsState> {

    private var state = ClaudixSettingsState()

    override fun getState(): ClaudixSettingsState {
        return state
    }

    override fun loadState(state: ClaudixSettingsState) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        /**
         * Get the singleton instance of ClaudixSettings
         */
        fun getInstance(): ClaudixSettings {
            return ApplicationManager.getApplication().getService(ClaudixSettings::class.java)
        }
    }
}
