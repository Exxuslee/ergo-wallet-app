package org.ergoplatform.desktop.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.ergoplatform.Application
import org.ergoplatform.WalletStateSyncManager
import org.ergoplatform.compose.settings.AppCard
import org.ergoplatform.compose.settings.mediumIconSize
import org.ergoplatform.compose.settings.smallIconSize
import org.ergoplatform.desktop.ui.AppScrollingLayout
import org.ergoplatform.desktop.ui.defaultMaxWidth
import org.ergoplatform.desktop.ui.defaultPadding
import org.ergoplatform.desktop.ui.uiErgoColor
import org.ergoplatform.desktop.wallet.addresses.ChooseAddressButton
import org.ergoplatform.mosaik.MosaikStyleConfig
import org.ergoplatform.mosaik.labelStyle
import org.ergoplatform.mosaik.model.ui.text.LabelStyle
import org.ergoplatform.uilogic.STRING_LABEL_ERG_AMOUNT
import org.ergoplatform.uilogic.STRING_TITLE_WALLET_ADDRESS
import org.ergoplatform.uilogic.STRING_TITLE_WALLET_BALANCE
import org.ergoplatform.uilogic.wallet.WalletDetailsUiLogic
import org.ergoplatform.utils.formatFiatToString

@Composable
fun WalletDetailsScreen(
    uiLogic: WalletDetailsUiLogic,
    onScanClicked: () -> Unit,
    onChooseAddressClicked: () -> Unit,
    onReceiveClicked: () -> Unit,
    onSendClicked: () -> Unit,
    onAddressesClicked: () -> Unit,
) {
    AppScrollingLayout {
        Column(
            Modifier.align(Alignment.TopCenter)
                .widthIn(min = 400.dp, max = defaultMaxWidth)
        ) {

            SelectAddressLayout(
                uiLogic,
                onChooseAddressClicked,
                onScanClicked,
                onReceiveClicked,
                onSendClicked,
                onAddressesClicked
            )

            ErgoBalanceLayout(uiLogic)
        }
    }
}

@Composable
private fun SelectAddressLayout(
    uiLogic: WalletDetailsUiLogic,
    onChooseAddressClicked: () -> Unit,
    onScanClicked: () -> Unit,
    onReceiveClicked: () -> Unit,
    onSendClicked: () -> Unit,
    onAddressesClicked: () -> Unit
) {
    AppCard(Modifier.padding(top = defaultPadding)) {
        Column(Modifier.padding(defaultPadding)) {

            Row {
                Icon(
                    Icons.Default.AltRoute,
                    null,
                    Modifier.requiredSize(mediumIconSize).align(Alignment.CenterVertically)
                )

                Column(Modifier.weight(1f).padding(start = defaultPadding)) {

                    Text(remember { Application.texts.getString(STRING_TITLE_WALLET_ADDRESS) })

                    ChooseAddressButton(
                        uiLogic.walletAddress,
                        uiLogic.wallet,
                        onClick = onChooseAddressClicked
                    )

                }

                IconButton(onScanClicked, Modifier.align(Alignment.Top)) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        null,
                        Modifier.requiredSize(smallIconSize)
                    )
                }
            }

            Row(Modifier.padding(top = defaultPadding / 2)) {
                Box(Modifier.requiredWidth(mediumIconSize))

                IconButton(onReceiveClicked, Modifier.weight(1f)) {
                    Icon(Icons.Default.CallReceived, null)
                }

                IconButton(onSendClicked, Modifier.weight(1f)) {
                    Icon(Icons.Default.CallMade, null)
                }

                IconButton(onAddressesClicked, Modifier.weight(1f)) {
                    Icon(Icons.Default.FormatListNumbered, null)
                }

            }
        }
    }
}

@Composable
private fun ErgoBalanceLayout(uiLogic: WalletDetailsUiLogic) {
    AppCard(Modifier.padding(top = defaultPadding)) {
        Column(Modifier.padding(defaultPadding)) {
            Row {
                Box(Modifier.requiredWidth(mediumIconSize))

                Text(
                    remember { Application.texts.getString(STRING_TITLE_WALLET_BALANCE) },
                    Modifier.weight(1f).padding(start = defaultPadding),
                    style = labelStyle(LabelStyle.BODY1BOLD),
                    color = uiErgoColor,
                )
            }

            val balanceErgoAmount = uiLogic.getErgoBalance()

            Row {
                Icon(
                    painterResource("ic_erglogo_filled.xml"), null,
                    Modifier.requiredSize(mediumIconSize)
                )

                val unconfirmed = uiLogic.getUnconfirmedErgoBalance().nanoErgs != 0L

                Text(
                    Application.texts.getString(
                        STRING_LABEL_ERG_AMOUNT, balanceErgoAmount.toString()
                    ) + (if (unconfirmed) "*" else ""),
                    Modifier.padding(start = defaultPadding).weight(1f),
                    style = labelStyle(LabelStyle.HEADLINE1)
                )

            }

            val walletSyncManager = WalletStateSyncManager.getInstance()
            if (walletSyncManager.hasFiatValue)
                Row {
                    Box(Modifier.requiredWidth(mediumIconSize))

                    Text(
                        formatFiatToString(
                            balanceErgoAmount.toDouble() * walletSyncManager.fiatValue.value,
                            walletSyncManager.fiatCurrency, Application.texts
                        ),
                        Modifier.padding(start = defaultPadding).weight(1f),
                        color = MosaikStyleConfig.secondaryLabelColor,
                        style = labelStyle(LabelStyle.BODY1)
                    )


                }

        }
    }
}
