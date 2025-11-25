// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.claudix

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Claudix Settings Configurable
 *
 * Provides a UI for configuring Claudix-specific settings in the IDE Settings dialog.
 * Maps to VSCode's Settings > Extensions > Claudix configuration.
 */
class ClaudixSettingsConfigurable : Configurable {

    private val settings = ClaudixSettings.getInstance()
    private var settingsPanel: JPanel? = null

    // UI Components
    private var selectedModelField: JBTextField? = null
    private val envVarRows = mutableListOf<Pair<JBTextField, JBTextField>>()

    override fun getDisplayName(): String = "Claudix"

    override fun createComponent(): JComponent {
        val panel = panel {
            group("Model Configuration") {
                row("Selected Model:") {
                    selectedModelField = textField()
                        .comment("The AI model for Claude Code (e.g., claude-3-5-sonnet-20241022)")
                        .component
                }
            }

            group("Environment Variables") {
                row {
                    comment("Environment variables to set when launching Claude")
                }

                // Display existing environment variables
                val state = settings.state
                for (envVar in state.environmentVariables) {
                    row {
                        val nameField = textField()
                            .label("Name:")
                            .component
                        nameField.text = envVar.name

                        val valueField = textField()
                            .label("Value:")
                            .component
                        valueField.text = envVar.value

                        envVarRows.add(Pair(nameField, valueField))
                    }
                }

                row {
                    button("Add Variable") {
                        // Add a new row for environment variable
                        val nameField = JBTextField()
                        val valueField = JBTextField()
                        envVarRows.add(Pair(nameField, valueField))
                        // Note: In a real implementation, you'd need to refresh the panel
                        // For simplicity, this is a basic version
                    }
                }
            }
        }

        settingsPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val state = settings.state

        // Check if selected model changed
        if (selectedModelField?.text != state.selectedModel) {
            return true
        }

        // Check if environment variables changed
        if (envVarRows.size != state.environmentVariables.size) {
            return true
        }

        for (i in envVarRows.indices) {
            val (nameField, valueField) = envVarRows[i]
            val envVar = state.environmentVariables.getOrNull(i)

            if (envVar == null ||
                nameField.text != envVar.name ||
                valueField.text != envVar.value) {
                return true
            }
        }

        return false
    }

    override fun apply() {
        val state = settings.state

        // Update selected model
        state.selectedModel = selectedModelField?.text ?: "default"

        // Update environment variables
        state.environmentVariables.clear()
        for ((nameField, valueField) in envVarRows) {
            val name = nameField.text?.trim() ?: ""
            val value = valueField.text?.trim() ?: ""

            if (name.isNotEmpty()) {
                state.environmentVariables.add(
                    EnvironmentVariable(name, value)
                )
            }
        }

        // Sync to PropertiesComponent so VSCode API can read the values
        syncToPropertiesComponent()
    }

    /**
     * Syncs ClaudixSettings to PropertiesComponent
     * This allows VSCode's workspace.getConfiguration() API to access the values
     */
    private fun syncToPropertiesComponent() {
        val properties = PropertiesComponent.getInstance()
        val state = settings.state

        // Sync selectedModel
        properties.setValue("claudix.selectedModel", state.selectedModel)

        // Sync environmentVariables as JSON-like string
        val envVarsJson = state.environmentVariables.joinToString(",") {
            "{\"name\":\"${it.name}\",\"value\":\"${it.value}\"}"
        }
        properties.setValue("claudix.environmentVariables", "[$envVarsJson]")
    }

    override fun reset() {
        val state = settings.state

        // Reset selected model field
        selectedModelField?.text = state.selectedModel

        // Reset environment variables
        envVarRows.clear()
        for (envVar in state.environmentVariables) {
            val nameField = JBTextField(envVar.name)
            val valueField = JBTextField(envVar.value)
            envVarRows.add(Pair(nameField, valueField))
        }
    }

    override fun disposeUIResources() {
        settingsPanel = null
        selectedModelField = null
        envVarRows.clear()
    }
}
