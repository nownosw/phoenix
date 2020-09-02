package fr.acinq.phoenix

import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.utils.screenProvider
import org.kodein.di.DI
import org.kodein.di.bind


class MockDIBuilder {
    var contentModel: Content.Model = Content.Model.NeedInitialization
    var initModel: Init.Model = Init.Model.Initialization
    var homeModel: Home.Model = Home.emptyModel
    var receiveModel: Receive.Model = Receive.Model.Generating
    var scanModel: Scan.Model = Scan.Model.Ready
    var restoreWalletModel: RestoreWallet.Model = RestoreWallet.Model.Ready

    fun apply(block: MockDIBuilder.() -> Unit): MockDIBuilder {
        this.block()
        return this
    }

    fun di() = DI {
        bind<ContentController>() with screenProvider { Content.MockController(contentModel) }
        bind<InitController>() with screenProvider { Init.MockController(initModel) }
        bind<HomeController>() with screenProvider { Home.MockController(homeModel) }
        bind<ReceiveController>() with screenProvider { Receive.MockController(receiveModel) }
        bind<ScanController>() with screenProvider { Scan.MockController(scanModel) }
        bind<RestoreWalletController>() with screenProvider { RestoreWallet.MockController(restoreWalletModel) }
    }
}
