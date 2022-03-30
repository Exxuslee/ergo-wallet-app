package org.ergoplatform.ios.transactions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ergoplatform.ErgoApiService
import org.ergoplatform.getExplorerAddressUrl
import org.ergoplatform.ios.tokens.TokenInformationViewController
import org.ergoplatform.ios.ui.*
import org.ergoplatform.persistance.Wallet
import org.ergoplatform.persistance.WalletAddress
import org.ergoplatform.transactions.TransactionListManager
import org.ergoplatform.uilogic.STRING_TRANSACTIONS_NONE_YET
import org.ergoplatform.uilogic.transactions.AddressTransactionWithTokens
import org.ergoplatform.wallet.addresses.getAddressLabel
import org.ergoplatform.wallet.getDerivedAddressEntity
import org.robovm.apple.coregraphics.CGPoint
import org.robovm.apple.coregraphics.CGRect
import org.robovm.apple.foundation.NSIndexPath
import org.robovm.apple.uikit.*
import kotlin.math.max

private const val transactionCellId = "TRANSACTION_CELL"
private const val emptyCellId = "EMPTY_CELL"
private const val pageLimit = 50

class AddressTransactionsViewController(
    private val walletId: Int,
    private val derivationIdx: Int
) : CoroutineViewController() {

    private val texts = getAppDelegate().texts
    private val tableView = UITableView(CGRect.Zero())

    private val shownData = ArrayList<AddressTransactionWithTokens>()
    private var nextPageToLoad = 0
    private var finishedLoading = false

    private var wallet: Wallet? = null
    private var shownAddress: WalletAddress? = null

    private lateinit var header: HeaderView

    override fun viewDidLoad() {
        val shareButton = UIBarButtonItem(UIBarButtonSystemItem.Action)
        navigationItem.rightBarButtonItem = shareButton
        shareButton.tintColor = UIColor.label()
        shareButton.setOnClickListener {
            shareText(getExplorerAddressUrl(shownAddress!!.publicAddress), shareButton)
        }

        header = HeaderView()
        view.addSubview(tableView)
        view.addSubview(header)
        header.widthMatchesSuperview(true).topToSuperview()
        tableView.widthMatchesSuperview(true).bottomToSuperview(true).topToBottomOf(header)

        tableView.dataSource = TransactionsDataSource()
        tableView.separatorStyle = UITableViewCellSeparatorStyle.None
        tableView.setAllowsSelection(false)
        val uiRefreshControl = UIRefreshControl()
        tableView.refreshControl = uiRefreshControl
        uiRefreshControl.addOnValueChangedListener {
            if (uiRefreshControl.isRefreshing) {
                uiRefreshControl.endRefreshing()
                refreshAddress()
            }
        }
        tableView.registerReusableCellClass(AddressTransactionCell::class.java, transactionCellId)
        tableView.registerReusableCellClass(EmptyCell::class.java, emptyCellId)
        tableView.rowHeight = UITableView.getAutomaticDimension()
        tableView.estimatedRowHeight = UITableView.getAutomaticDimension()
    }

    private fun refreshAddress() {
        val appDelegate = getAppDelegate()
        TransactionListManager.downloadTransactionListForAddress(
            shownAddress!!.publicAddress,
            ErgoApiService.getOrInit(appDelegate.prefs),
            appDelegate.database
        )
    }

    override fun viewWillAppear(p0: Boolean) {
        super.viewWillAppear(p0)
        val appDelegate = getAppDelegate()
        viewControllerScope.launch(Dispatchers.IO) {
            appDelegate.database.walletDbProvider.loadWalletWithStateById(walletId)?.let { wallet ->
                this@AddressTransactionsViewController.wallet = wallet
                shownAddress = wallet.getDerivedAddressEntity(derivationIdx)
                onResume()
                runOnMainThread { newAddressChosen() }
            }
        }
        viewControllerScope.launch {
            TransactionListManager.isDownloading.collect { refreshing ->
                runOnMainThread {
                    header.isRefreshing = refreshing
                    if (!refreshing) {
                        runOnMainThread {
                            // TODO only do when at top of list to avoid user getting interrupted
                            refreshListShownData()
                        }
                    }
                }
            }
        }
        // TODO observe TransactionListManager.downloadProgress and refresh list
    }

    private fun newAddressChosen() {
        header.addressLabel.text = shownAddress?.getAddressLabel(IosStringProvider(texts))
        refreshListShownData()
    }

    private fun refreshListShownData() {
        // complete refresh
        nextPageToLoad = 0
        finishedLoading = false
        shownData.clear()
        fetchNextChunkFromDb()
    }

    private fun fetchNextChunkFromDb() {
        if (finishedLoading)
            return

        val pageToLoad = nextPageToLoad
        shownAddress?.let { address ->
            viewControllerScope.launch {
                val txLoaded = getAppDelegate().database.transactionDbProvider.loadAddressTransactionsWithTokens(
                    address.publicAddress,
                    pageLimit, pageToLoad
                )
                runOnMainThread {
                    shownData.addAll(txLoaded)
                    if (pageToLoad == 0) {
                        // yes, this is needed
                        // https://stackoverflow.com/a/50606137/7487013
                        tableView.setContentOffset(CGPoint.Zero(), false)
                    }
                    tableView.reloadData()
                    if (pageToLoad == 0) {
                        tableView.layoutIfNeeded()
                        tableView.setContentOffset(CGPoint.Zero(), false)
                    }
                }
                nextPageToLoad = pageToLoad + 1
                finishedLoading = txLoaded.isEmpty()
            }
        }
    }

    override fun onResume() {
        refreshAddress()
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        tableView.refreshControl.endRefreshing()
    }

    inner class TransactionsDataSource : UITableViewDataSourceAdapter() {
        override fun getNumberOfRowsInSection(p0: UITableView?, p1: Long): Long {
            // When we already have data to show, show the empty cell if no wallets configured
            return max(1, shownData.size.toLong())
        }

        override fun getCellForRow(p0: UITableView, p1: NSIndexPath): UITableViewCell {
            if (shownData.isEmpty()) {
                return p0.dequeueReusableCell(emptyCellId)
            } else {
                val cell = p0.dequeueReusableCell(transactionCellId)
                val itemIndex = p1.row
                (cell as? AddressTransactionCell)?.bind(shownData[itemIndex], this@AddressTransactionsViewController)
                if (itemIndex == shownData.size - 1) {
                    fetchNextChunkFromDb()
                    // TODO show item for last element
                }
                return cell
            }
        }

        override fun getNumberOfSections(p0: UITableView?): Long {
            return 1
        }

        override fun canEditRow(p0: UITableView?, p1: NSIndexPath?): Boolean {
            return false
        }

        override fun canMoveRow(p0: UITableView?, p1: NSIndexPath?): Boolean {
            return false
        }
    }

    inner class HeaderView : UIView(CGRect.Zero()) {
        private val refreshView = UIActivityIndicatorView(UIActivityIndicatorViewStyle.Medium)
        private val addressSelector =
            buildAddressSelectorView(
                this@AddressTransactionsViewController, walletId,
                showAllAddresses = false,
                keepWidth = true
            ) {
                shownAddress = wallet?.getDerivedAddressEntity(it!!)
                newAddressChosen()
            }
        val addressLabel get() = addressSelector.content

        var isRefreshing: Boolean = false
            set(refreshing) {
                field = refreshing
                if (refreshing) {
                    refreshView.startAnimating()
                } else {
                    refreshView.stopAnimating()
                }
            }

        init {
            addSubview(refreshView)
            addSubview(addressSelector)
            refreshView.rightToSuperview().topToSuperview().bottomToSuperview().fixedWidth(30.0)
            addressSelector.centerVertical().centerHorizontal(true)
            backgroundColor = UIColor.secondarySystemBackground()
        }

    }

    class EmptyCell : AbstractTableViewCell(emptyCellId) {
        override fun setupView() {
            val label = Body1Label().apply {
                text = getAppDelegate().texts.get(STRING_TRANSACTIONS_NONE_YET)
                textAlignment = NSTextAlignment.Center
            }
            contentView.addSubview(label)
            label.centerHorizontal(true).topToSuperview(topInset = DEFAULT_MARGIN * 10).bottomToSuperview()
        }

    }

    class AddressTransactionCell : AbstractTableViewCell(transactionCellId) {
        private lateinit var txView: TransactionEntryView

        override fun setupView() {
            val cardView = CardView()
            txView = TransactionEntryView()

            contentView.addSubview(cardView)

            cardView.widthMatchesSuperview(true, DEFAULT_MARGIN, MAX_WIDTH)
                .superViewWrapsHeight(true, 0.0)

            cardView.contentView.addSubview(txView)
            cardView.contentView.layoutMargins = UIEdgeInsets.Zero()
            txView.edgesToSuperview(inset = DEFAULT_MARGIN)
        }

        fun bind(tx: AddressTransactionWithTokens, vc: UIViewController) {
            txView.bind(tx, tokenClickListener = { tokenId ->
                vc.presentViewController(TokenInformationViewController(tokenId, null), true) {}
            }, getAppDelegate().texts)
        }

    }
}