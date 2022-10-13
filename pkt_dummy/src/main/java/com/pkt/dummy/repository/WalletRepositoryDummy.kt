package com.pkt.dummy.repository

import android.content.Context
import com.pkt.domain.dto.*
import com.pkt.domain.repository.WalletRepository
import com.pkt.dummy.AddrMapper
import com.pkt.dummy.CjdnsInfoMapper
import com.pkt.dummy.R
import com.pkt.dummy.WalletInfoMapper
import com.pkt.dummy.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.mapstruct.factory.Mappers
import kotlin.random.Random

@OptIn(ExperimentalSerializationApi::class)
class WalletRepositoryDummy constructor(
    private val context: Context,
) : WalletRepository {

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val addrMapper by lazy {
        Mappers.getMapper(AddrMapper::class.java)
    }

    private val walletInfoMapper by lazy {
        Mappers.getMapper(WalletInfoMapper::class.java)
    }

    private val cjdnsInfoMapper by lazy {
        Mappers.getMapper(CjdnsInfoMapper::class.java)
    }

    override suspend fun getAllWalletNames(): Result<List<String>> {
        TODO("Not yet implemented")
    }

    override suspend fun getActiveWallet(): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun setActiveWallet(walletName: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getWalletAddress(): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun isPinAvailable(): Result<Boolean> {
        delay(1000)
        return Result.success(true)
    }

    override suspend fun checkPin(pin: String): Result<Boolean> {
        delay(1000L)
        return if (pin == "1111") {
            Result.success(true)
        } else {
            Result.success(false)
        }
    }

    override suspend fun getWalletBalance(address: String): Result<Double> {
        return Result.success(0.0)
    }

    override suspend fun getWalletInfo(): Result<WalletInfo> = withContext(Dispatchers.IO) {
        runCatching {
            delay(1000L)
            val info = context.resources.openRawResource(R.raw.wallet_info_1).use {
                json.decodeFromStream<WalletInfoDummy>(it)
            }
            walletInfoMapper.map(info)
        }
    }

    override suspend fun getCjdnsInfo(address: String): Result<CjdnsInfo> = withContext(Dispatchers.IO) {
        runCatching {
            delay(1000L)
            val info = context.resources.openRawResource(R.raw.cjdns_info).use {
                json.decodeFromStream<CjdnsInfoDummy>(it)
            }
            cjdnsInfoMapper.map(info)
        }
    }

    override suspend fun generateSeed(password: String, pin: String) = withContext(Dispatchers.IO) {
        Result.success("Tail net similar exercise scan sting buddy oil during museum outside cluster extra aim")
    }

    override suspend fun createWallet(password: String, pin: String, seed: String) = withContext(Dispatchers.IO) {
        delay(5_000L)
        Result.success(Unit)
    }

    override suspend fun recoverWallet(password: String, seed: String) = withContext(Dispatchers.IO) {
        delay(1_000L)
        Result.success(Unit)
    }

    override suspend fun unlockWallet(passphrase: String): Result<Boolean> {
        TODO("Not yet implemented")
    }

    override suspend fun unlockWalletWithPIN(pin: String): Result<Boolean> {
        TODO("Not yet implemented")
    }

    override suspend fun createAddress(): String {
        TODO("Not yet implemented")
    }

    override suspend fun getWalletBalances(): Result<WalletAddressBalances> {
        TODO("Not yet implemented")
    }

    override suspend fun getCurrentAddress(): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getWalletTransactions(): Result<WalletTransactions> {
        TODO("Not yet implemented")
    }
}
