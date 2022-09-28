/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.android.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.WalletContext


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SendSwapOutView(
    model: Scan.Model.SwapOutFlow,
    maxFees: MaxFees?,
    onBackClick: () -> Unit,
    onInvalidate: (Scan.Intent.SwapOutFlow.Invalidate) -> Unit,
    onPrepareSwapOutClick: (Scan.Intent.SwapOutFlow.PrepareSwapOut) -> Unit,
    onSendSwapOutClick: (Scan.Intent.SwapOutFlow.SendSwapOut) -> Unit,
) {
    val log = logger("SendSwapOutView")
    log.info { "init swapout amount=${model.address.amount?.toMilliSatoshi()} to=${model.address.address}" }

    val context = LocalContext.current
    val prefBtcUnit = LocalBitcoinUnit.current
    val keyboardManager = LocalSoftwareKeyboardController.current

    val requestedAmount = model.address.amount
    var amount by remember { mutableStateOf(model.address.amount) }
    var amountErrorMessage by remember { mutableStateOf("") }
    val balance = business.peerManager.balance.collectAsState(null).value
    val swapOutConfig = business.appConfigurationManager.chainContext.collectAsState(initial = null).value?.swapOut?.v1 ?: WalletContext.V0.SwapOut.V1(
        minFeerateSatByte = 20,
        minAmountSat = 10_000,
        maxAmountSat = 2_000_000L,
        _status = 0
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 50.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackButtonWithBalance(onBackClick = onBackClick, balance = balance)
        Spacer(Modifier.height(16.dp))
        Card(
            externalPadding = PaddingValues(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            AmountHeroInput(
                initialAmount = amount?.toMilliSatoshi(),
                onAmountChange = {
                    amountErrorMessage = ""
                    val newAmount = it?.amount?.truncateToSatoshi()
                    if (model !is Scan.Model.SwapOutFlow.Init && amount != newAmount) {
                        // if amount changes after a swap-out request has already been prepared, we must start over again
                        onInvalidate(Scan.Intent.SwapOutFlow.Invalidate(model.address))
                    }
                    when {
                        newAmount == null -> {}
                        newAmount < swapOutConfig.minAmountSat.sat -> {
                            amountErrorMessage = context.getString(
                                R.string.send_swapout_error_amount_too_small,
                                swapOutConfig.minAmountSat.sat.toMilliSatoshi().toPrettyString(prefBtcUnit, rate = null, withUnit = true)
                            )
                        }
                        newAmount > swapOutConfig.maxAmountSat.sat -> {
                            amountErrorMessage = context.getString(
                                R.string.send_swapout_error_amount_too_large,
                                swapOutConfig.maxAmountSat.sat.toMilliSatoshi().toPrettyString(prefBtcUnit, rate = null, withUnit = true)
                            )
                        }
                        balance != null && newAmount > balance.truncateToSatoshi() -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_over_balance)
                        }
                        requestedAmount != null && newAmount < requestedAmount -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_below_requested)
                        }
                    }
                    amount = newAmount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp
            )
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(text = stringResource(R.string.send_destination_label)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhoenixIcon(resourceId = R.drawable.ic_chain, modifier = Modifier.size(18.dp), tint = MaterialTheme.colors.primary)
                        Spacer(Modifier.width(4.dp))
                        SelectionContainer {
                            Text(text = model.address.address, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (model) {
            is Scan.Model.SwapOutFlow.Init -> {
                BorderButton(
                    text = stringResource(id = R.string.send_swapout_prepare_button),
                    icon = R.drawable.ic_build,
                    enabled = amountErrorMessage.isBlank(),
                ) {
                    amount?.let {
                        keyboardManager?.hide()
                        onPrepareSwapOutClick(Scan.Intent.SwapOutFlow.PrepareSwapOut(address = model.address, amount = it))
                    } ?: run {
                        amountErrorMessage = context.getString(R.string.send_error_amount_invalid)
                    }
                }
            }
            is Scan.Model.SwapOutFlow.RequestingSwapout -> {
                Text(stringResource(id = R.string.send_swapout_prepare_in_progress))
            }
            is Scan.Model.SwapOutFlow.SwapOutReady -> {
                val total = model.paymentRequest.amount?.truncateToSatoshi()
                if (total == null || total == 0.sat || total != model.initialUserAmount + model.fee) {
                    onInvalidate(Scan.Intent.SwapOutFlow.Invalidate(model.address))
                } else {
                    SwapOutFeeSummaryView(userAmount = model.initialUserAmount, fee = model.fee, total = total)
                    Spacer(modifier = Modifier.height(24.dp))
                    if (balance != null && total.toMilliSatoshi() > balance) {
                        Text(
                            text = stringResource(R.string.send_swapout_error_cannot_afford_fees),
                            style = MaterialTheme.typography.body1.copy(color = negativeColor(), fontSize = 14.sp), maxLines = 1
                        )
                    } else {
                        FilledButton(
                            text = R.string.send_pay_button,
                            icon = R.drawable.ic_send,
                            enabled = amountErrorMessage.isBlank()
                        ) {
                            onSendSwapOutClick(
                                Scan.Intent.SwapOutFlow.SendSwapOut(
                                    amount = total,
                                    paymentRequest = model.paymentRequest,
                                    maxFees = maxFees,
                                    address = model.address,
                                    swapOutFee = model.fee
                                )
                            )
                        }
                    }
                }
            }
            is Scan.Model.SwapOutFlow.SendingSwapOut -> {
                LaunchedEffect(key1 = Unit) { onBackClick() }
            }
        }
    }
}

@Composable
private fun SwapOutFeeSummaryView(
    userAmount: Satoshi,
    fee: Satoshi,
    total: Satoshi,
) {
    Card(
        internalPadding = PaddingValues(16.dp),
        modifier = Modifier.widthIn(max = 300.dp),
        withBorder = true,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FeeSummary(label = stringResource(id = R.string.send_swapout_complete_recap_amount), amount = userAmount.toMilliSatoshi())
        FeeSummary(label = stringResource(id = R.string.send_swapout_complete_recap_fee), amount = fee.toMilliSatoshi())
        FeeSummary(label = stringResource(id = R.string.send_swapout_complete_recap_total), amount = total.toMilliSatoshi())
    }
}

@Composable
private fun FeeSummary(
    label: String,
    amount: fr.acinq.lightning.MilliSatoshi
) {
    Row(
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.body1.copy(color = mutedTextColor(), fontSize = 12.sp),
            textAlign = TextAlign.End,
            modifier = Modifier
                .alignBy(FirstBaseline)
                .weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .alignBy(FirstBaseline)
                .weight(2f)
        ) {
            AmountWithFiatView(amount = amount, amountTextStyle = MaterialTheme.typography.body2)
        }
    }
}
