package jp.co.soramitsu.iroha2

import jp.co.soramitsu.iroha2.engine.ALICE_ACCOUNT_ID
import jp.co.soramitsu.iroha2.engine.ALICE_ACCOUNT_NAME
import jp.co.soramitsu.iroha2.engine.ALICE_KEYPAIR
import jp.co.soramitsu.iroha2.engine.AliceAndBobEachHave100Xor
import jp.co.soramitsu.iroha2.engine.AliceHas100XorAndPermissionToBurn
import jp.co.soramitsu.iroha2.engine.DEFAULT_ASSET_ID
import jp.co.soramitsu.iroha2.engine.DEFAULT_DOMAIN_ID
import jp.co.soramitsu.iroha2.engine.IrohaRunnerExtension
import jp.co.soramitsu.iroha2.engine.WithIroha
import jp.co.soramitsu.iroha2.generated.core.time.Duration
import jp.co.soramitsu.iroha2.generated.datamodel.Name
import jp.co.soramitsu.iroha2.generated.datamodel.asset.AssetValue
import jp.co.soramitsu.iroha2.generated.datamodel.asset.AssetValueType
import jp.co.soramitsu.iroha2.generated.datamodel.asset.DefinitionId
import jp.co.soramitsu.iroha2.generated.datamodel.events.time.Schedule
import jp.co.soramitsu.iroha2.generated.datamodel.isi.Instruction
import jp.co.soramitsu.iroha2.generated.datamodel.trigger.Repeats
import jp.co.soramitsu.iroha2.query.QueryBuilder
import jp.co.soramitsu.iroha2.transaction.Instructions
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.math.BigInteger
import java.security.KeyPair
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import jp.co.soramitsu.iroha2.generated.datamodel.account.Id as AccountId
import jp.co.soramitsu.iroha2.generated.datamodel.asset.Id as AssetId
import jp.co.soramitsu.iroha2.generated.datamodel.trigger.Id as TriggerId

@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(IrohaRunnerExtension::class)
@Timeout(40)
class TriggersTest {

    lateinit var client: Iroha2Client

    @Test
    @WithIroha(AliceHas100XorAndPermissionToBurn::class)
    fun `data created trigger`(): Unit = runBlocking {
        val triggerId = TriggerId("data_trigger".asName())
        val domainId = "wonderland".asDomainId()
        val newAssetName = "token1"

        // check account assets before trigger
        val query = QueryBuilder.findAllAssetsDefinitions()
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
        val assetDefinitions = client.sendQuery(query)
        assertEquals(1, assetDefinitions.size)
        val asset = assetDefinitions.filter { it.id.name.string == "xor" }.first()
        assertNotNull(asset)

        // Check default asset quantity before trigger
        val prevQuantity = QueryBuilder.findDomainById(domainId)
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
            .let { query -> client.sendQuery(query) }
            .accounts
            .filter { it.key.name == ALICE_ACCOUNT_NAME }
            .map { it.value.assets[DEFAULT_ASSET_ID] }
            .map { (it?.value as AssetValue.Quantity).u32 }
            .first()

        assertEquals(100L, prevQuantity)

        // register trigger
        client.sendTransaction {
            accountId = ALICE_ACCOUNT_ID
            registerDataCreatedEventTrigger(
                triggerId,
                listOf(Instructions.mintAsset(DEFAULT_ASSET_ID, 1L)),
                Repeats.Indefinitely(),
                ALICE_ACCOUNT_ID
            )
            executeTrigger(triggerId)
            buildSigned(ALICE_KEYPAIR)
        }.also {
            Assertions.assertDoesNotThrow {
                it.get(10, TimeUnit.SECONDS)
            }
        }

        // register new asset
        // after that trigger should mint DEFAULT_ASSET_ID
        createNewAsset(newAssetName)

        // check new quantity after trigger is run
        val newQuantity = QueryBuilder.findDomainById(domainId)
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
            .let { query -> client.sendQuery(query) }
            .accounts
            .filter { it.key.name == ALICE_ACCOUNT_NAME }
            .map { it.value.assets[DEFAULT_ASSET_ID] }
            .map { (it?.value as AssetValue.Quantity).u32 }
            .first()
        assertEquals(prevQuantity + 1L, newQuantity)
    }

    private suspend fun createNewAsset(assetName: String) {
        val newAsset = DefinitionId(assetName.asName(), DEFAULT_DOMAIN_ID)
        client.sendTransaction {
            accountId = ALICE_ACCOUNT_ID
            registerAsset(newAsset, AssetValueType.Quantity())
            buildSigned(ALICE_KEYPAIR)
        }.also {
            Assertions.assertDoesNotThrow {
                it.get(10, TimeUnit.SECONDS)
            }
        }

        // check new asset is created
        val query = QueryBuilder.findAllAssetsDefinitions()
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
        val assetDefinitions = client.sendQuery(query)
        assertEquals(2, assetDefinitions.size)
        val asset = assetDefinitions.filter { it.id.name.string == assetName }.first()
        assertNotNull(asset)
    }

    @Test
    @WithIroha(AliceHas100XorAndPermissionToBurn::class)
    fun `pre commit trigger should mint asset to account for every transaction`(): Unit = runBlocking {
        val triggerId = TriggerId("pre_commit_trigger".asName())
        val domainId = "wonderland".asDomainId()
        val newAssetName = "token1"

        // check DEFAULT_ASSET_ID quantity before trigger
        val prevQuantity = QueryBuilder.findDomainById(domainId)
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
            .let { query -> client.sendQuery(query) }
            .accounts
            .filter { it.key.name == ALICE_ACCOUNT_NAME }
            .map { it.value.assets[DEFAULT_ASSET_ID] }
            .map { (it?.value as AssetValue.Quantity).u32 }
            .first()
        assertEquals(100L, prevQuantity)

        // register pre commit trigger
        client.sendTransaction {
            accountId = ALICE_ACCOUNT_ID
            registerPreCommitTrigger(
                triggerId,
                listOf(Instructions.mintAsset(DEFAULT_ASSET_ID, 10L)),
                Repeats.Indefinitely(),
                ALICE_ACCOUNT_ID
            )
            executeTrigger(triggerId)
            buildSigned(ALICE_KEYPAIR)
        }.also {
            Assertions.assertDoesNotThrow {
                it.get(10, TimeUnit.SECONDS)
            }
        }

        // check DEFAULT_ASSET_ID quantity after trigger is run
        var newQuantity = QueryBuilder.findDomainById(domainId)
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
            .let { query -> client.sendQuery(query) }
            .accounts
            .filter { it.key.name == ALICE_ACCOUNT_NAME }
            .map { it.value.assets[DEFAULT_ASSET_ID] }
            .map { (it?.value as AssetValue.Quantity).u32 }
            .first()
        assertEquals(110L, newQuantity)

        // register new asset
        // after that trigger should mint 10 more quantity to DEFAULT_ASSET_ID
        createNewAsset(newAssetName)

        // check DEFAULT_ASSET_ID quantity after trigger is run
        newQuantity = QueryBuilder.findDomainById(domainId)
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
            .let { query -> client.sendQuery(query) }
            .accounts
            .filter { it.key.name == ALICE_ACCOUNT_NAME }
            .map { it.value.assets[DEFAULT_ASSET_ID] }
            .map { (it?.value as AssetValue.Quantity).u32 }
            .first()
        assertEquals(120L, newQuantity)

        // transfer asset instruction just to test trigger
        val bobAssetId = AliceAndBobEachHave100Xor.BOB_ASSET_ID
        client.sendTransaction {
            account(ALICE_ACCOUNT_ID)
            transferAsset(DEFAULT_ASSET_ID, 100, bobAssetId)
            buildSigned(ALICE_KEYPAIR)
        }.also {
            Assertions.assertDoesNotThrow {
                it.get(10, TimeUnit.SECONDS)
            }
        }

        // check DEFAULT_ASSET_ID quantity after trigger is run
        newQuantity = QueryBuilder.findDomainById(domainId)
            .account(ALICE_ACCOUNT_ID)
            .buildSigned(ALICE_KEYPAIR)
            .let { query -> client.sendQuery(query) }
            .accounts
            .filter { it.key.name == ALICE_ACCOUNT_NAME }
            .map { it.value.assets[DEFAULT_ASSET_ID] }
            .map { (it?.value as AssetValue.Quantity).u32 }
            .first()
        assertEquals(30L, newQuantity)
    }

    @Test
    @WithIroha(AliceHas100XorAndPermissionToBurn::class)
    fun `executable trigger`(): Unit = runBlocking {
        val triggerId = TriggerId("executable_trigger".asName())

        client.sendTransaction {
            accountId = ALICE_ACCOUNT_ID
            registerExecutableTrigger(
                triggerId,
                listOf(Instructions.mintAsset(DEFAULT_ASSET_ID, 1L)),
                Repeats.Exactly(1L),
                ALICE_ACCOUNT_ID
            )
            executeTrigger(triggerId)
            buildSigned(ALICE_KEYPAIR)
        }.also {
            Assertions.assertDoesNotThrow {
                it.get(10, TimeUnit.SECONDS)
            }
        }

        assertEquals(101L, readQuantity())
    }

    @Test
    @WithIroha(AliceHas100XorAndPermissionToBurn::class)
    fun `endless time trigger`(): Unit = runBlocking {
        val triggerId = TriggerId(Name("endless_time_trigger"))

        sendAndAwaitTimeTrigger(
            triggerId,
            Repeats.Indefinitely(),
            Instructions.burnAsset(DEFAULT_ASSET_ID, 1L)
        )
        sendAndWait10Txs()

        delay(3000)
        assert(readQuantity() <= 90L)
    }

    @Test
    @WithIroha(AliceHas100XorAndPermissionToBurn::class)
    fun `time trigger execution repeats few times`(): Unit = runBlocking {
        val triggerId = TriggerId(Name("time_trigger"))

        sendAndAwaitTimeTrigger(
            triggerId,
            Repeats.Exactly(5L),
            Instructions.burnAsset(DEFAULT_ASSET_ID, 1L)
        )
        sendAndWait10Txs()

        delay(3000)
        assertEquals(95, readQuantity())
    }

    private suspend fun sendAndWait10Txs() {
        for (i in 0..10) {
            client.sendTransaction {
                accountId = ALICE_ACCOUNT_ID
                setKeyValue(ALICE_ACCOUNT_ID, "key$i".asName(), "value$i".asValue())
                buildSigned(ALICE_KEYPAIR)
            }.also {
                delay(1000)
                Assertions.assertDoesNotThrow {
                    it.get(10, TimeUnit.SECONDS)
                }
            }
        }
    }

    private suspend fun readQuantity(
        assetId: AssetId = DEFAULT_ASSET_ID,
        accountId: AccountId = ALICE_ACCOUNT_ID,
        keyPair: KeyPair = ALICE_KEYPAIR
    ): Long {
        return QueryBuilder.findAssetById(assetId)
            .account(accountId)
            .buildSigned(keyPair)
            .let { query -> client.sendQuery(query) }
            .value.cast<AssetValue.Quantity>().u32
    }

    private suspend fun sendAndAwaitTimeTrigger(
        triggerId: TriggerId,
        repeats: Repeats,
        instruction: Instruction,
        accountId: AccountId = ALICE_ACCOUNT_ID,
    ) {
        client.sendTransaction {
            this.accountId = accountId
            registerTimeTrigger(
                triggerId, listOf(instruction), repeats, accountId,
                Schedule(
                    Duration(BigInteger.valueOf(Instant.now().epochSecond), 0L),
                    Duration(BigInteger.valueOf(1L), 0L)
                )
            )
            buildSigned(ALICE_KEYPAIR)
        }.also {
            Assertions.assertDoesNotThrow {
                it.get(10, TimeUnit.SECONDS)
            }
        }
    }
}
