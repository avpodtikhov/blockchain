package com.wavesplatform.we.app.example.contract.impl

import com.wavesplatform.vst.contract.data.ContractCall
import com.wavesplatform.vst.contract.mapping.Mapping
import com.wavesplatform.vst.contract.spring.annotation.ContractHandlerBean
import com.wavesplatform.vst.contract.state.ContractState
import com.wavesplatform.vst.contract.state.setValue
import com.wavesplatform.vst.contract.state.getValue
import com.wavesplatform.we.app.example.contract.ExampleContract

@ContractHandlerBean
class ExampleContractImpl(
    state: ContractState,
    private val call: ContractCall
) : ExampleContract {
    private val buyers: Mapping<Buyer> by state
    private val producers: Mapping<Producer> by state
    private val hubs: Mapping<Hub> by state
    private val carriers: Mapping<Carrier> by state
    private val moneyHolder: Mapping<Int> by state
    private val orders: Mapping<MutableList<Order>> by state
    private val chains: Mapping<SupplyChain> by state

    override fun invoke() {
        TODO("Not yet implemented")
    }

    override fun create() {
        val buyer = Buyer(
                name = "Buyer",
                key = "3QVUnXdCq7vMJDjfcuM7G5qCqFKeBQuac8P",
                money = 100
        )
        buyers.put("3QVUnXdCq7vMJDjfcuM7G5qCqFKeBQuac8P", buyer)
        val product = Product(
                name="Brick",
                num="123456",
                storage_conditions = "empty",
                category = 1,
                unit_price = 5
        )
        val producer = Producer(
                name = "Producer",
                key = "3QZdm3KxwX4GRdrdGLxeZ4xmEyrv5rEXCqF",
                money = 100,
                product = product
        )
        producers.put("3QZdm3KxwX4GRdrdGLxeZ4xmEyrv5rEXCqF", producer)
        val hub = Hub(
                name = "Hub",
                key = "3Qbur18V2vQfJEcnkqM3H9ieNozVup4mG2v",
                unit_price = 1,
                money = 100
        )
        hubs.put("3Qbur18V2vQfJEcnkqM3H9ieNozVup4mG2v", hub)
        val carrier1 = Carrier(
                name = "Carrier 1",
                key = "3QZdm3KxwX4GRdrdGLxeZ4xmEyrv5rEXCqF",
                money = 100,
                category = 1,
                unit_price=2
        )
        carriers.put("3QZdm3KxwX4GRdrdGLxeZ4xmEyrv5rEXCqF", carrier1)
        val carrier2 = Carrier(
                name = "Carrier 2",
                key = "3QVUnXdCq7vMJDjfcuM7G5qCqFKeBQuac8P",
                money = 100,
                category = 1,
                unit_price=1
        )
        carriers.put("3QVUnXdCq7vMJDjfcuM7G5qCqFKeBQuac8P", carrier2)
    }

    override fun makeOrder(product_name: String, producer_key: String, count: Int) {
        require(buyers.has(call.sender)) {
            "ONLY_REGISTERED_BUYER_CAN_PLACE_ORDERS"
        }
        require(producers.has(producer_key)) {
            "PRODUCER_IS_NOT_REGISTERED"
        }
        val producer = producers[producer_key]
        require(producer.product.name == product_name) {
            "PRODUCER_DOEST_NOT_CREATE_SUCH_PRODUCT"
        }
        val buyer = buyers[call.sender]
        require(buyer.money >= count * producer.product.unit_price) {
            "NOT_ENOUGH_MONEY"
        }
        val order = Order(count = count, buyer_key = buyer.key)
        var ordersList: MutableList<Order> = ArrayList()
        if (orders.has(producer.key)) {
            ordersList = orders[producer.key]
        }
        ordersList.add(order)
        orders.put(producer.key, ordersList)
    }

    override fun acceptOrder(order_id: Int, supply_chain: String) {
        require(producers.has(call.sender)) {
            "ONLY_PRODUCERS_CAN_ACCEPT_ORDERS"
        }
        require(orders.has(call.sender)) {
            "YOU_HAVE_NO_ORDERS_YET"
        }
        require(order_id in 0..orders[call.sender].size) {
            "INVALID_ORDER_INDEX"
        }
        val supply_chain = supply_chain.split(", ").toTypedArray().toCollection(ArrayList())
        val order = orders[call.sender].elementAt(order_id)
        val count = order.count
        var sum = 0
        for (supplier_idx in supply_chain.indices) {
            require(hubs.has(supply_chain.elementAt(supplier_idx)) or carriers.has(supply_chain.elementAt(supplier_idx))) {
                "ONLY_HUBS_AND_CARRIERS_ALLOWED_IN_SUPPLY_CHAIN"
            }
            if (hubs.has(supply_chain.elementAt(supplier_idx))) {
                sum += hubs[supply_chain.elementAt(supplier_idx)].unit_price * count
            } else {
                require(carriers[supply_chain.elementAt(supplier_idx)].category == producers[call.sender].product.category) {
                    "CHOSEN_SUPPLIERS_DO_NOT_TAKE_PRODUCTS_OF_THIS_CATEGORY"
                }
                sum += carriers[supply_chain.elementAt(supplier_idx)].unit_price * count
            }
        }
        require(sum < producers[call.sender].product.unit_price * count) {
            "THE_DELIVERY_PRICE_EXCEEDS_THE_VALUE_OF_PRODUCT"
        }
        val buyer = buyers[order.buyer_key]
        val newBuyerState = Buyer(name=buyer.name, key=buyer.key, money=buyer.money - count * producers[call.sender].product.unit_price)
        buyers.put(buyer.key, newBuyerState)
        var newHolderState: Int = count * producers[call.sender].product.unit_price
        if (moneyHolder.has(buyer.key)) {
            newHolderState += moneyHolder[buyer.key]
        }
        moneyHolder.put(buyer.key, newHolderState)
        val status = arrayListOf<String>()
        status.add(call.sender)
        supply_chain.add(buyer.key)
        val chain = SupplyChain(supplyOrder = supply_chain, status = status, product_key = producers[call.sender].product.name, count = count)
        chains.put(producers[call.sender].key, chain) // сейчас на одного поставщика добавялется только одна цепь
    }

    override fun validateOrder(prev_holder_key: String) {
        require(chains.has(prev_holder_key)) {
            "INVALID_PREV_HOLDER_KEY"
        }
        require(chains[prev_holder_key].supplyOrder.elementAt(chains[prev_holder_key].status.size) == call.sender) {
            "NOT_YOUR_TURN"
        }
        val buyerKey = chains[prev_holder_key].supplyOrder.elementAt(-1)
        val producerKey = chains[prev_holder_key].status.elementAt(-1)
        if (producers.has(producerKey)) {
            moneyHolder.put(buyerKey, moneyHolder[buyerKey] - chains[prev_holder_key].count * producers[producerKey].product.unit_price)
            producers.put(producerKey, Producer(name=producers[producerKey].name, product = producers[producerKey].product, key=producers[producerKey].key, money = producers[producerKey].money+chains[prev_holder_key].count * producers[producerKey].product.unit_price))
        } else {
            if (hubs.has(producerKey)) {
                moneyHolder.put(buyerKey, moneyHolder[buyerKey] - chains[prev_holder_key].count * hubs[producerKey].unit_price)
                hubs.put(producerKey, Hub(key = hubs[producerKey].key, name = hubs[producerKey].name, unit_price = hubs[producerKey].unit_price, money = hubs[producerKey].money + chains[prev_holder_key].count * hubs[producerKey].unit_price))
            } else {
                moneyHolder.put(buyerKey, moneyHolder[buyerKey] - chains[prev_holder_key].count * carriers[producerKey].unit_price)
                carriers.put(producerKey, Carrier(key = carriers[producerKey].key, name = carriers[producerKey].name, unit_price = carriers[producerKey].unit_price, money = carriers[producerKey].money + chains[prev_holder_key].count * carriers[producerKey].unit_price, category = carriers[producerKey].category))
            }
        }
        chains[prev_holder_key].status.add(call.sender)
//        val buyer = chains[prev_holder_key].supplyOrder.elementAt(-1)
//        if (producers.has(chains[prev_holder_key].status.elementAt(-1))) {

//        }
//        moneyHolder.put(buyer, moneyHolder[buyer])
//        moneyHolder[buyer]
    }
}

data class Buyer(
        val name:String,
        val key: String,
        val money: Int
)

data class Producer(
        val name:String,
        val key: String,
        val money: Int,
        val product: Product
)

data class Product(
        val num: String,
        val storage_conditions: String,
        val category: Int,
        val unit_price: Int,
        val name: String
        )

data class Hub(
        val name:String,
        val key: String,
        val money: Int,
        val unit_price: Int
)

data class Carrier(
        val name:String,
        val key: String,
        val money: Int,
        val category: Int,
        val unit_price: Int
)

data class Order(
        val count: Int,
        val buyer_key: String
)

data class SupplyChain(
        val supplyOrder: ArrayList<String>,
        val status: ArrayList<String>,
        val count: Int,
        val product_key: String
)