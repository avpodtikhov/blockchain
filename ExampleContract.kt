package com.wavesplatform.we.app.example.contract

import com.wavesplatform.vst.contract.ContractAction
import com.wavesplatform.vst.contract.ContractInit
import com.wavesplatform.vst.contract.InvokeParam

interface ExampleContract {

    @ContractInit
    fun create()

    @ContractAction
    fun invoke()

    @ContractAction
    fun makeOrder(
            @InvokeParam(name="product_name") product_name: String,
            @InvokeParam(name="producer_key") producer_key: String,
            @InvokeParam(name="count") count: Int
    )

    @ContractAction
    fun acceptOrder(
            @InvokeParam(name="order_id") order_id: Int,
            @InvokeParam(name="supply_chain") supply_chain: String
    )
    @ContractAction
    fun validateOrder(
            @InvokeParam(name="prev_holder_key") prev_holder_key: String
    )
}
