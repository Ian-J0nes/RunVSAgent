// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package com.sina.weibo.agent.extensions.plugin.claudix

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.ui.dsl.builder.*
import com.sina.weibo.agent.core.PluginContext
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Claudix Settings Configurable
 *
 * Provides a UI for configuring Claudix-specific settings in the IDE Settings dialog.
 * Maps to VSCode's Settings > Extensions > Claudix configuration.
 */
class ClaudixSettingsConfigurable : Configurable {

    private val settings = ClaudixSettings.getInstance()
    private var settingsPanel: JPanel? = null
    private var selectedModelField: com.intellij.ui.components.JBTextField? = null
    private lateinit var envTableModel: DefaultTableModel
    private lateinit var envTable: JBTable

    override fun getDisplayName(): String = "Claudix"

    override fun createComponent(): JComponent {
        // 创建表格模型：两列（Name, Value）
        envTableModel = object : DefaultTableModel(
            arrayOf("Name", "Value"),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int): Boolean = true
        }

        // 从设置加载现有环境变量
        val state = settings.state
        for (envVar in state.environmentVariables) {
            envTableModel.addRow(arrayOf(envVar.name, envVar.value))
        }

        // 创建表格
        envTable = JBTable(envTableModel)

        // 使用 ToolbarDecorator 包装表格,添加 +/- 按钮
        val decorator = ToolbarDecorator.createDecorator(envTable)
            .setAddAction {
                // 点击 + 按钮时添加新行
                envTableModel.addRow(arrayOf("", ""))
                val newRow = envTableModel.rowCount - 1
                envTable.setRowSelectionInterval(newRow, newRow)
                envTable.editCellAt(newRow, 0)
                envTable.transferFocus()
            }
            .setRemoveAction {
                // 点击 - 按钮时删除选中行
                val selectedRow = envTable.selectedRow
                if (selectedRow >= 0) {
                    envTableModel.removeRow(selectedRow)
                }
            }
            .setRemoveActionUpdater {
                // 只有选中行时才启用删除按钮
                envTable.selectedRow >= 0
            }
            .disableUpDownActions() // 禁用上下移动按钮

        val panel = panel {
            group("Model Configuration") {
                row("Selected Model:") {
                    selectedModelField = textField()
                        .text(state.selectedModel)
                        .comment("The AI model for Claude Code (e.g., claude-3-5-sonnet-20241022)")
                        .component
                }
            }

            group("Environment Variables") {
                row {
                    comment("Environment variables to set when launching Claude")
                }
                row {
                    // 添加带工具栏的表格
                    cell(decorator.createPanel())
                        .align(Align.FILL)
                }.resizableRow()
            }
        }

        settingsPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val state = settings.state

        // 检查 selected model 是否修改
        if (selectedModelField?.text != state.selectedModel) {
            return true
        }

        // 检查环境变量数量是否变化
        if (envTableModel.rowCount != state.environmentVariables.size) {
            return true
        }

        // 检查每个环境变量是否修改
        for (i in 0 until envTableModel.rowCount) {
            val name = envTableModel.getValueAt(i, 0) as? String ?: ""
            val value = envTableModel.getValueAt(i, 1) as? String ?: ""
            val envVar = state.environmentVariables.getOrNull(i)

            if (envVar == null ||
                name != envVar.name ||
                value != envVar.value) {
                return true
            }
        }

        return false
    }

    override fun apply() {
        val state = settings.state

        // 更新 selected model
        state.selectedModel = selectedModelField?.text ?: "default"

        // 更新环境变量
        state.environmentVariables.clear()
        for (i in 0 until envTableModel.rowCount) {
            val name = (envTableModel.getValueAt(i, 0) as? String ?: "").trim()
            val value = (envTableModel.getValueAt(i, 1) as? String ?: "").trim()

            // 只保存非空的环境变量
            if (name.isNotEmpty()) {
                state.environmentVariables.add(
                    EnvironmentVariable(name, value)
                )
            }
        }

        // 同步到 MainThreadConfiguration (这会触发 VSCode API 的 config.update)
        syncToMainThreadConfiguration()
    }

    /**
     * Syncs settings through MainThreadConfiguration and Extension Host RPC
     * This triggers VSCode's workspace.getConfiguration().update() which persists to storage
     */
    private fun syncToMainThreadConfiguration() {
        val state = settings.state

        println("[CLAUDIX-DEBUG] ========== syncToMainThreadConfiguration START ==========")
        println("[CLAUDIX-DEBUG] selectedModel: ${state.selectedModel}")
        println("[CLAUDIX-DEBUG] environmentVariables count: ${state.environmentVariables.size}")
        state.environmentVariables.forEachIndexed { index, envVar ->
            println("[CLAUDIX-DEBUG] envVar[$index]: name=${envVar.name}, value=${envVar.value}")
        }

        // 1. Sync to PropertiesComponent (for JetBrains side storage)
        val properties = PropertiesComponent.getInstance()
        properties.setValue("claudix.selectedModel", state.selectedModel)

        val envVarsJson = state.environmentVariables.joinToString(",") {
            "{\"name\":\"${it.name}\",\"value\":\"${it.value}\"}"
        }
        properties.setValue("claudix.environmentVariables", "[$envVarsJson]")
        println("[CLAUDIX-DEBUG] Saved to PropertiesComponent")

        // 2. Sync to Extension Host via RPC updateConfiguration
        try {
            // Find an active project to get PluginContext
            val activeProject = getActiveProject()
            println("[CLAUDIX-DEBUG] Active project: ${activeProject?.name}")

            if (activeProject != null) {
                val pluginContext = PluginContext.getInstance(activeProject)
                val rpcProtocol = pluginContext.getRPCProtocol()

                println("[CLAUDIX-DEBUG] RPC protocol available: ${rpcProtocol != null}")

                if (rpcProtocol != null) {
                    // Get ExtHostConfiguration proxy
                    val extHostConfiguration = rpcProtocol.getProxy(
                        com.sina.weibo.agent.core.ServiceProxyRegistry.ExtHostContext.ExtHostConfiguration
                    )

                    println("[CLAUDIX-DEBUG] Got ExtHostConfiguration proxy: $extHostConfiguration")

                    // Build configuration model with Claudix settings
                    val envVars = state.environmentVariables.map { envVar ->
                        mapOf("name" to envVar.name, "value" to envVar.value)
                    }

                    val claudixConfig = mapOf(
                        "claudix.selectedModel" to state.selectedModel,
                        "claudix.environmentVariables" to envVars
                    )

                    println("[CLAUDIX-DEBUG] Built claudixConfig: $claudixConfig")

                    val configModel = mapOf(
                        "defaults" to mapOf(
                            "contents" to claudixConfig,
                            "keys" to claudixConfig.keys.toList(),
                            "overrides" to emptyList<String>()
                        ),
                        "policy" to emptyMap<String, Any>(),
                        "application" to emptyMap<String, Any>(),
                        "userLocal" to emptyMap<String, Any>(),
                        "userRemote" to emptyMap<String, Any>(),
                        "workspace" to emptyMap<String, Any>(),
                        "folders" to emptyList<Any>(),
                        "configurationScopes" to emptyList<Any>()
                    )

                    println("[CLAUDIX-DEBUG] Calling extHostConfiguration.updateConfiguration()...")
                    // Update configuration via RPC
                    extHostConfiguration.updateConfiguration(configModel)
                    println("[CLAUDIX-DEBUG] Successfully called updateConfiguration()")
                    println("[CLAUDIX-DEBUG] Updated Claudix configuration via RPC: model=${state.selectedModel}, envVars=${envVars.size} items")
                } else {
                    println("[CLAUDIX-DEBUG] RPC protocol not available, Claudix configuration not synced to Extension Host")
                }
            } else {
                println("[CLAUDIX-DEBUG] No active project found, Claudix configuration not synced to Extension Host")
            }
        } catch (e: Exception) {
            println("[CLAUDIX-DEBUG] Failed to sync Claudix configuration to Extension Host: ${e.message}")
            e.printStackTrace()
        }

        println("[CLAUDIX-DEBUG] ========== syncToMainThreadConfiguration END ==========")
    }

    /**
     * Get the currently active project
     */
    private fun getActiveProject(): Project? {
        val openProjects = ProjectManager.getInstance().openProjects
        return openProjects.firstOrNull { it.isInitialized && !it.isDisposed }
    }

    override fun reset() {
        val state = settings.state

        // 重置 selected model 字段
        selectedModelField?.text = state.selectedModel

        // 重置环境变量表格
        envTableModel.rowCount = 0
        for (envVar in state.environmentVariables) {
            envTableModel.addRow(arrayOf(envVar.name, envVar.value))
        }
    }

    override fun disposeUIResources() {
        settingsPanel = null
        selectedModelField = null
    }
}
