package fr.acinq.phoenix.ctrl

import fr.acinq.eklair.utils.Connection
import fr.acinq.phoenix.app.Transaction
import fr.acinq.phoenix.utils.plus


typealias HomeController = MVI.Controller<Home.Model, Home.Intent>

object Home {

    data class Model(
        val connections: Connections,
        val balanceSat: Long,
        val history: List<Transaction>
    ) : MVI.Model() {
//        data class Channel(val cid: String, val local: Long, val remote: Long, val state: String)
    }

    val emptyModel = Model(Connections(), 0, emptyList())

    sealed class Intent : MVI.Intent() {
        object Connect : Intent()
    }

    class MockController(model: Model): MVI.Controller.Mock<Model, Intent>(model)

}

data class Connections(
    val internet: Connection = Connection.CLOSED,
    val peer: Connection = Connection.CLOSED,
    val electrum: Connection = Connection.CLOSED
) {
    val global : Connection
        get() = internet + peer + electrum
}
