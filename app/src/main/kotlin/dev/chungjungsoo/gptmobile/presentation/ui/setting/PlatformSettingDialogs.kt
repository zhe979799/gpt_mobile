package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.ModelConstants.anthropicModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.deepseekModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.getDefaultAPIUrl
import dev.chungjungsoo.gptmobile.data.ModelConstants.googleModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.groqModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.ollamaModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.openaiModels
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.common.TokenInputField
import dev.chungjungsoo.gptmobile.util.*
import kotlin.math.roundToInt

@Composable
fun APIUrlDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    initialValue: String,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isApiUrlDialogOpen) {
        APIUrlDialog(
            apiType = apiType,
            initialValue = initialValue,
            onDismissRequest = settingViewModel::closeApiUrlDialog,
            onResetRequest = {
                settingViewModel.updateURL(apiType, getDefaultAPIUrl(apiType))
                settingViewModel.savePlatformSettings()
                settingViewModel.closeApiUrlDialog()
            },
            onConfirmRequest = { apiUrl ->
                settingViewModel.updateURL(apiType, apiUrl)
                settingViewModel.savePlatformSettings()
                settingViewModel.closeApiUrlDialog()
            }
        )
    }
}

@Composable
fun APIKeyDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isApiTokenDialogOpen) {
        APIKeyDialog(
            apiType = apiType,
            onDismissRequest = settingViewModel::closeApiTokenDialog
        ) { apiToken ->
            settingViewModel.updateToken(apiType, apiToken)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeApiTokenDialog()
        }
    }
}

@Composable
fun ModelDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    model: String?,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isApiModelDialogOpen) {
        ModelDialog(
            apiType = apiType,
            initModel = model ?: "",
            onDismissRequest = settingViewModel::closeApiModelDialog
        ) { m ->
            settingViewModel.updateModel(apiType, m)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeApiModelDialog()
        }
    }
}

@Composable
fun TemperatureDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    temperature: Float,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isTemperatureDialogOpen) {
        TemperatureDialog(
            apiType = apiType,
            temperature = temperature,
            onDismissRequest = settingViewModel::closeTemperatureDialog
        ) { temp ->
            settingViewModel.updateTemperature(apiType, temp)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeTemperatureDialog()
        }
    }
}

@Composable
fun MaxTokensDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    maxTokens: Int,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isMaxTokensDialogOpen) {
        IntegerAdjustDialog(
            apiType = apiType,
            currentValue = maxTokens,
            onDismissRequest = settingViewModel::closeMaxTokensDialog
        ) { newValue ->
            settingViewModel.updateMaxTokens(apiType, newValue)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeMaxTokensDialog()
        }
    }
}

@Composable
fun TopPDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    topP: Float?,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isTopPDialogOpen) {
        TopPDialog(
            topP = topP,
            onDismissRequest = settingViewModel::closeTopPDialog
        ) { p ->
            settingViewModel.updateTopP(apiType, p)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeTopPDialog()
        }
    }
}

@Composable
fun WebSearchDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    model: String?,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isWebsearchDialogOpen) {
        ModelDialog(
            apiType = apiType,
            initModel = model ?: "",
            onDismissRequest = settingViewModel::closeWebsearchDialog
        ) { m ->
            Log.e("websearch", "WebSearchDialog: $model")
            settingViewModel.updateWebsearch(apiType, m)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeWebsearchDialog()
        }
    }
}

@Composable
fun SystemPromptDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    systemPrompt: String,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isSystemPromptDialogOpen) {
        SystemPromptDialog(
            prompt = systemPrompt,
            onDismissRequest = settingViewModel::closeSystemPromptDialog
        ) {
            settingViewModel.updateSystemPrompt(apiType, it)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeSystemPromptDialog()
        }
    }
}

@Composable
private fun APIUrlDialog(
    apiType: ApiType,
    initialValue: String,
    onDismissRequest: () -> Unit,
    onResetRequest: () -> Unit,
    onConfirmRequest: (url: String) -> Unit
) {
    var apiUrl by remember { mutableStateOf(initialValue) }
    val configuration = LocalConfiguration.current

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.api_url)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.api_url_cautions)
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    value = apiUrl,
                    singleLine = true,
                    isError = apiUrl.isValidUrl().not(),
                    onValueChange = { apiUrl = it },
                    label = {
                        Text(stringResource(R.string.api_url))
                    },
                    supportingText = {
                        if (apiUrl.isValidUrl().not()) {
                            Text(text = stringResource(R.string.invalid_api_url))
                        }
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = apiUrl.isNotBlank() && apiUrl.isValidUrl() && apiUrl.endsWith("/"),
                onClick = { onConfirmRequest(apiUrl) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = onResetRequest
                ) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun APIKeyDialog(
    apiType: ApiType,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (token: String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = getPlatformAPILabelResources()[apiType]!!) },
        text = {
            TokenInputField(
                value = token,
                onValueChange = { token = it },
                onClearClick = { token = "" },
                label = getPlatformAPILabelResources()[apiType]!!,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                helpLink = getPlatformHelpLinkResources()[apiType]!!
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = token.isNotBlank(),
                onClick = { onConfirmRequest(token) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ModelDialog(
    apiType: ApiType,
    initModel: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (model: String) -> Unit
) {
    val modelList = when (apiType) {
        ApiType.OPENAI -> openaiModels
        ApiType.ANTHROPIC -> anthropicModels
        ApiType.GOOGLE -> googleModels
        ApiType.GROQ -> groqModels
        ApiType.DEEPSEEK -> deepseekModels
        ApiType.OLLAMA -> ollamaModels
    }
    val availableModels = when (apiType) {
        ApiType.OPENAI -> generateOpenAIModelList(models = modelList)
        ApiType.ANTHROPIC -> generateAnthropicModelList(models = modelList)
        ApiType.GOOGLE -> generateGoogleModelList(models = modelList)
        ApiType.GROQ -> generateGroqModelList(models = modelList)
        ApiType.DEEPSEEK -> generateDeepseekModelList(models = modelList)
        ApiType.OLLAMA -> listOf()
    }
    val configuration = LocalConfiguration.current
    var model by remember { mutableStateOf(initModel) }
    var customSelected by remember { mutableStateOf(model !in availableModels.map { it.aliasValue }.toSet()) }
    var customModel by remember { mutableStateOf(if (customSelected) model else "") }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.api_model)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                availableModels.forEach { m ->
                    RadioItem(
                        value = m.aliasValue,
                        selected = model == m.aliasValue && !customSelected,
                        title = m.name,
                        description = m.description,
                        onSelected = {
                            model = it
                            customSelected = false
                        }
                    )
                }
                RadioItem(
                    value = customModel,
                    selected = customSelected,
                    title = stringResource(R.string.custom),
                    description = stringResource(R.string.custom_description),
                    onSelected = {
                        customSelected = true
                        customModel = it
                    }
                )
                OutlinedTextField(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    enabled = customSelected,
                    value = customModel,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onValueChange = { s -> customModel = s },
                    label = {
                        Text(stringResource(R.string.model_name))
                    },
                    placeholder = {
                        Text(stringResource(R.string.model_custom_example))
                    },
                    supportingText = {
                        Text(stringResource(R.string.custom_model_warning))
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = if (customSelected) customModel.isNotBlank() else model.isNotBlank(),
                onClick = {
                    if (customSelected) {
                        onConfirmRequest(customModel)
                    } else {
                        onConfirmRequest(model)
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun IntegerAdjustDialog(
    apiType: ApiType,
    currentValue: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    // 根据API类型设置不同的取值范围
    val (minValue, maxValue) = when (apiType) {
        ApiType.DEEPSEEK -> 1 to 8192   // Anthropic的特殊范围
        else -> 1 to 1_024 * 20              // 其他API的默认范围
    }

    var textFieldValue by remember { mutableStateOf(currentValue.toString()) }
    var localValue by remember { mutableIntStateOf(currentValue) }

    // 当外部currentValue变化时同步状态（如重新打开对话框）
    LaunchedEffect(currentValue) {
        localValue = currentValue.coerceIn(minValue, maxValue)
        textFieldValue = localValue.toString()
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text("调整 ${apiType.name} 的Max Tokens") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 说明文本
                Text("设置生成内容的最大令牌数量。数值越大生成内容越详细，但消耗也越多。")

                // 文本输入框
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldValue,
                    onValueChange = { newText ->
                        textFieldValue = newText
                        newText.toIntOrNull()?.let { value ->
                            if (value in minValue..maxValue) {
                                localValue = value
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("最大令牌数") }
                )

                // 滑动条组件
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = localValue.toFloat(),
                    valueRange = minValue.toFloat()..maxValue.toFloat(),
                    steps = maxValue - minValue - 1, // 每个整数作为一个步长
                    onValueChange = { newValue ->
                        val intValue = newValue.roundToInt()
                        localValue = intValue
                        textFieldValue = intValue.toString()
                    }
                )

                // 实时显示当前值
                Text(
                    text = "当前值: $localValue",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(localValue) }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TemperatureDialog(
    apiType: ApiType,
    temperature: Float,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (temp: Float) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldTemperature by remember { mutableStateOf(temperature.toString()) }
    var sliderTemperature by remember { mutableFloatStateOf(temperature) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.temperature_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.temperature_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTemperature,
                    onValueChange = { t ->
                        textFieldTemperature = t
                        val converted = t.toFloatOrNull()
                        converted?.let {
                            sliderTemperature = when (apiType) {
                                ApiType.ANTHROPIC -> it.coerceIn(0F, 1F)
                                else -> it.coerceIn(0F, 2F)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.temperature))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTemperature,
                    valueRange = when (apiType) {
                        ApiType.ANTHROPIC -> 0F..1F
                        else -> 0F..2F
                    },
                    steps = when (apiType) {
                        ApiType.ANTHROPIC -> 10 - 1
                        else -> 20 - 1
                    },
                    onValueChange = { t ->
                        sliderTemperature = t
                        textFieldTemperature = t.toString()
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(sliderTemperature) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TopPDialog(
    topP: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (topP: Float) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldTopP by remember { mutableStateOf((topP ?: 1F).toString()) }
    var sliderTopP by remember { mutableFloatStateOf(topP ?: 1F) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.top_p_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.top_p_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTopP,
                    onValueChange = { p ->
                        textFieldTopP = p
                        p.toFloatOrNull()?.let {
                            val rounded = (it.coerceIn(0.1F, 1F) * 100).roundToInt() / 100F
                            sliderTopP = rounded
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.top_p))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTopP,
                    valueRange = 0.1F..1F,
                    steps = 8,
                    onValueChange = { t ->
                        val rounded = (t * 100).roundToInt() / 100F
                        sliderTopP = rounded
                        textFieldTopP = rounded.toString()
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(sliderTopP) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SystemPromptDialog(
    prompt: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (text: String) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldPrompt by remember { mutableStateOf(prompt) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.system_prompt_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.system_prompt_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldPrompt,
                    onValueChange = { textFieldPrompt = it },
                    label = {
                        Text(stringResource(R.string.system_prompt))
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = textFieldPrompt.isNotBlank(),
                onClick = { onConfirmRequest(textFieldPrompt) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
