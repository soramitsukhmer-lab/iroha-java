//
// Auto-generated file. DO NOT EDIT!
//
package jp.co.soramitsu.iroha2.generated

import jp.co.soramitsu.iroha2.codec.ScaleCodecReader
import jp.co.soramitsu.iroha2.codec.ScaleCodecWriter
import jp.co.soramitsu.iroha2.codec.ScaleReader
import jp.co.soramitsu.iroha2.codec.ScaleWriter
import jp.co.soramitsu.iroha2.wrapException
import kotlin.Any
import kotlin.Unit
import kotlin.collections.List

/**
 * BatchedResponseV1
 *
 * Generated from 'BatchedResponseV1<Vec<SignedTransaction>>' regular structure
 */
public data class BatchedResponseV1<T0>(
    public val batch: List<SignedTransaction>,
    public val cursor: ForwardCursor,
) {
    public companion object :
        ScaleReader<BatchedResponseV1<out Any>>,
        ScaleWriter<BatchedResponseV1<out Any>> {
        override fun read(reader: ScaleCodecReader): BatchedResponseV1<out Any> = try {
            BatchedResponseV1(
                reader.readVec(reader.readCompactInt()) { SignedTransaction.read(reader) },
                ForwardCursor.read(reader),
            )
        } catch (ex: Exception) {
            throw wrapException(ex)
        }

        override fun write(writer: ScaleCodecWriter, instance: BatchedResponseV1<out Any>): Unit = try {
            writer.writeCompact(instance.batch.size)
            instance.batch.forEach { value ->
                SignedTransaction.write(writer, value)
            }
            ForwardCursor.write(writer, instance.cursor)
        } catch (ex: Exception) {
            throw wrapException(ex)
        }
    }
}
