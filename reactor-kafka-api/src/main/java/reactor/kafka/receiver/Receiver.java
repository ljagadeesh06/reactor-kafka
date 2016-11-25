/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package reactor.kafka.receiver;

import java.util.function.Function;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.internals.ConsumerFactory;
import reactor.kafka.receiver.internals.KafkaReceiver;

/**
 * A reactive Kafka receiver for consuming records from topic partitions
 * of a Kafka cluster.
 *
 * @param <K> incoming record key type
 * @param <V> incoming record value type
 */
public interface Receiver<K, V> {

    /**
     * Creates a reactive Kafka receiver with the specified configuration options.
     *
     * @param options Configuration options of this receiver. Changes made to the options
     *        after the receiver is created will not be used by the receiver.
     *        A subscription using group management or a manual assignment of topic partitions
     *        must be set on the options instance prior to creating this receiver.
     * @return new receiver instance
     */
    public static <K, V> Receiver<K, V> create(ReceiverOptions<K, V> options) {
        return new KafkaReceiver<>(ConsumerFactory.INSTANCE, options);
    }

    /**
     * Starts a Kafka consumer that consumes records from the subscriptions or partition
     * assignments configured for this receiver. Records are consumed from Kafka and delivered
     * on the returned Flux when requests are made on the Flux. The Kafka consumer is closed
     * after when the returned Flux terminates.
     * <p>
     * Every record must be acknowledged using {@link ReceiverOffset#acknowledge()} in order
     * to commit the offset corresponding to the record. Records may also be committed manually
     * using {@link ReceiverOffset#commit()}.
     *
     * @return Flux of inbound records that are committed only after acknowledgement
     */
    Flux<ReceiverRecord<K, V>> receive();

    /**
     * Returns a {@link Flux} containing each batch of consumer records returned by {@link Consumer#poll(long)}.
     * {@link ConsumerConfig#MAX_POLL_RECORDS_CONFIG} can be configured on {@link ReceiverOptions} to
     * control the maximum number of records in a batch. Each batch is returned as one Flux. All the
     * records in a batch are acknowledged automatically when its Flux terminates. Acknowledged records
     * are committed periodically based on the configured commit interval and commit batch size of
     * this receiver's {@link ReceiverOptions}.
     *
     * @return Flux of consumer record batches from Kafka that are auto-acknowledged
     */
    Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck();

    /**
     * Returns a {@link Flux} of consumer records that are committed before the record is dispatched
     * to provide atmost-once delivery semantics. The offset of each record dispatched on the
     * returned Flux is committed synchronously to ensure that the record is not re-delivered
     * if the application fails. This mode is expensive since each method is committed individually
     * and records are not delivered until the commit operation succeeds.
     * @return Flux of consumer records whose offsets have been committed prior to dispatch
     */
    Flux<ConsumerRecord<K, V>> receiveAtmostOnce();

    /**
     * Invokes the specified function on the Kafka {@link Consumer} associated with this Receiver.
     * The function is scheduled when the returned {@link Mono} is subscribed to. It is executed
     * on the thread used for other consumer operations to ensure that consumer is never
     * accessed concurrently from multiple threads.
     * <p>
     * Example usage:
     * <pre>
     * {@code
     *     receiver.doOnConsumer(consumer -> consumer.partitionsFor(topic))
     *           .doOnSuccess(partitions -> System.out.println("Partitions " + partitions));
     * }
     * </pre>
     * Functions that are directly supported through the reactive receiver interface
     * like <code>poll</code> and <code>commit</code> should not be invoked from <code>function</code>.
     * The methods supported by <code>doOnConsumer</code> are:
     * <ul>
     *   <li>{@link Consumer#assignment()}
     *   <li>{@link Consumer#subscription()}
     *   <li>{@link Consumer#seek(org.apache.kafka.common.TopicPartition, long)}
     *   <li>{@link Consumer#seekToBeginning(java.util.Collection)}
     *   <li>{@link Consumer#seekToEnd(java.util.Collection)}
     *   <li>{@link Consumer#position(org.apache.kafka.common.TopicPartition)}
     *   <li>{@link Consumer#committed(org.apache.kafka.common.TopicPartition)}
     *   <li>{@link Consumer#metrics()}
     *   <li>{@link Consumer#partitionsFor(String)}
     *   <li>{@link Consumer#listTopics()}
     *   <li>{@link Consumer#paused()}
     *   <li>{@link Consumer#pause(java.util.Collection)}
     *   <li>{@link Consumer#resume(java.util.Collection)}
     *   <li>{@link Consumer#offsetsForTimes(java.util.Map)}
     *   <li>{@link Consumer#beginningOffsets(java.util.Collection)}
     *   <li>{@link Consumer#endOffsets(java.util.Collection)}
     * </ul>
     *
     * @param function A function that takes Kafka {@link Consumer} as parameter
     * @return Mono that completes with the value returned by <code>function</code>
     */
    <T> Mono<T> doOnConsumer(Function<Consumer<K, V>, ? extends T> function);
}
