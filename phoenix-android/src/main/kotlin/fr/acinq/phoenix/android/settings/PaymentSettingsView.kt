/*
 * Copyright 2022 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalWalletContext
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.Converter
import fr.acinq.phoenix.android.utils.Converter.proportionalFeeAsPercentage
import fr.acinq.phoenix.android.utils.Converter.proportionalFeeAsPercentageString
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.PaymentOptionsConstants
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun PaymentSettingsView(
    initialShowLnurlAuthSchemeDialog: Boolean = false,
) {
    val log = logger("PaymentSettingsView")
    val nc = navController
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val btcUnit = LocalBitcoinUnit.current

    var showDescriptionDialog by rememberSaveable { mutableStateOf(false) }
    var showExpiryDialog by rememberSaveable { mutableStateOf(false) }

    val invoiceDefaultDesc by UserPrefs.getInvoiceDefaultDesc(LocalContext.current).collectAsState(initial = "")
    val invoiceDefaultExpiry by UserPrefs.getInvoiceDefaultExpiry(LocalContext.current).collectAsState(initial = -1L)

    val prefLnurlAuthSchemeState = UserPrefs.getLnurlAuthScheme(context).collectAsState(initial = null)

    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.paymentsettings_title),
        )

        CardHeader(text = stringResource(id = R.string.paymentsettings_category_incoming))
        Card {
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_defaultdesc_title),
                description = invoiceDefaultDesc.ifEmpty { stringResource(id = R.string.paymentsettings_defaultdesc_none) },
                onClick = { showDescriptionDialog = true }
            )
            SettingInteractive(
                title = stringResource(id = R.string.paymentsettings_expiry_title),
                description = when (invoiceDefaultExpiry) {
                    1 * DateUtils.WEEK_IN_MILLIS / 1_000 -> stringResource(id = R.string.paymentsettings_expiry_one_week)
                    2 * DateUtils.WEEK_IN_MILLIS / 1_000 -> stringResource(id = R.string.paymentsettings_expiry_two_weeks)
                    3 * DateUtils.WEEK_IN_MILLIS / 1_000 -> stringResource(id = R.string.paymentsettings_expiry_three_weeks)
                    else -> stringResource(id = R.string.paymentsettings_expiry_value, NumberFormat.getInstance().format(invoiceDefaultExpiry))
                },
                onClick = { showExpiryDialog = true }
            )
        }

        val prefLnurlAuthScheme = prefLnurlAuthSchemeState.value
        if (prefLnurlAuthScheme != null) {
            CardHeader(text = stringResource(id = R.string.paymentsettings_category_lnurl))
            Card {
                val schemes = listOf<PreferenceItem<LnurlAuth.Scheme>>(
                    PreferenceItem(
                        item = LnurlAuth.Scheme.DEFAULT_SCHEME,
                        title = stringResource(id = R.string.lnurl_auth_scheme_default),
                        description = stringResource(id = R.string.lnurl_auth_scheme_default_desc)
                    ),
                    PreferenceItem(
                        item = LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME,
                        title = stringResource(id = R.string.lnurl_auth_scheme_legacy),
                        description = stringResource(id = R.string.lnurl_auth_scheme_legacy_desc)
                    ),
                )
                ListPreferenceButton(
                    title = stringResource(id = R.string.paymentsettings_lnurlauth_scheme_title),
                    subtitle = {
                        when (prefLnurlAuthScheme) {
                            LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME -> Text(text = stringResource(id = R.string.lnurl_auth_scheme_legacy))
                            LnurlAuth.Scheme.DEFAULT_SCHEME -> Text(text = stringResource(id = R.string.lnurl_auth_scheme_default))
                            else -> Text(text = stringResource(id = R.string.utils_unknown))
                        }
                    },
                    enabled = true,
                    selectedItem = prefLnurlAuthScheme,
                    preferences = schemes,
                    onPreferenceSubmit = {
                        scope.launch { UserPrefs.saveLnurlAuthScheme(context, it.item) }
                    },
                    initialShowDialog = initialShowLnurlAuthSchemeDialog
                )
            }
        }
    }

    if (showDescriptionDialog) {
        DefaultDescriptionInvoiceDialog(
            description = invoiceDefaultDesc,
            onDismiss = { showDescriptionDialog = false },
            onConfirm = {
                scope.launch { UserPrefs.saveInvoiceDefaultDesc(context, it) }
                showDescriptionDialog = false
            }
        )
    }

    if (showExpiryDialog) {
        DefaultExpiryInvoiceDialog(
            expiry = invoiceDefaultExpiry,
            onDismiss = { showExpiryDialog = false },
            onConfirm = {
                scope.launch { UserPrefs.saveInvoiceDefaultExpiry(context, it.toLong()) }
                showExpiryDialog = false
            }
        )
    }

}

@Composable
private fun DefaultExpiryInvoiceDialog(
    expiry: (Long),
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var paymentExpiry by rememberSaveable { mutableStateOf(expiry.toFloat()) }

    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.paymentsettings_expiry_dialog_title),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = { onConfirm(paymentExpiry) },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(id = R.string.paymentsettings_expiry_dialog_description))
            Spacer(Modifier.height(16.dp))
            Slider(
                value = paymentExpiry,
                onValueChange = { paymentExpiry = it },
                valueRange = 604800f..1814400f,
                steps = 1,
            )
            Row {
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_one_week),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_two_weeks),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(id = R.string.paymentsettings_expiry_three_weeks),
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DefaultDescriptionInvoiceDialog(
    description: (String),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var paymentDescription by rememberSaveable { mutableStateOf(description) }

    Dialog(
        onDismiss = onDismiss,
        title = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_title),
        buttons = {
            Button(onClick = onDismiss, text = stringResource(id = R.string.btn_cancel))
            Button(
                onClick = { onConfirm(paymentDescription) },
                text = stringResource(id = R.string.btn_ok)
            )
        }
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(text = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_description))
            Spacer(Modifier.height(24.dp))
            TextInput(
                modifier = Modifier.fillMaxWidth(),
                text = paymentDescription,
                staticLabel = stringResource(id = R.string.paymentsettings_defaultdesc_dialog_label),
                onTextChange = { paymentDescription = it },
                maxLines = 3,
                maxChars = 180,
            )
        }
    }
}
