/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.rule.engine.transform;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.queue.TbMsgCallback;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TbRenameKeysNodeTest {
    DeviceId deviceId;
    TbRenameKeysNode node;
    TbRenameKeysNodeConfiguration config;
    TbNodeConfiguration nodeConfiguration;
    TbContext ctx;
    TbMsgCallback callback;

    @BeforeEach
    void setUp() throws TbNodeException {
        deviceId = new DeviceId(UUID.randomUUID());
        callback = mock(TbMsgCallback.class);
        ctx = mock(TbContext.class);
        config = new TbRenameKeysNodeConfiguration().defaultConfiguration();
        config.setRenameKeysMapping(Map.of("TestKey_1", "Attribute_1", "TestKey_2", "Attribute_2"));
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node = spy(new TbRenameKeysNode());
        node.init(ctx, nodeConfiguration);
    }

    @AfterEach
    void tearDown() {
        node.destroy();
    }

    @Test
    void givenDefaultConfig_whenVerify_thenOK() {
        TbRenameKeysNodeConfiguration defaultConfig = new TbRenameKeysNodeConfiguration().defaultConfiguration();
        assertThat(defaultConfig.getRenameKeysMapping()).isEqualTo(Map.of("temp", "temperature"));
        assertThat(defaultConfig.isFromMetadata()).isEqualTo(false);
    }

    @Test
    void givenMsg_whenOnMsg_thenVerifyOutput() throws Exception {
        String data = "{\"Temperature_1\":22.5,\"TestKey_2\":10.3}";
        node.onMsg(ctx, getTbMsg(deviceId, data));

        ArgumentCaptor<TbMsg> newMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        verify(ctx, times(1)).tellSuccess(newMsgCaptor.capture());
        verify(ctx, never()).tellFailure(any(), any());

        TbMsg newMsg = newMsgCaptor.getValue();
        assertThat(newMsg).isNotNull();

        JsonNode dataNode = JacksonUtil.toJsonNode(newMsg.getData());
        assertThat(dataNode.has("Attribute_2")).isEqualTo(true);
        assertThat(dataNode.has("Temperature_1")).isEqualTo(true);
    }

    @Test
    void givenMetadata_whenOnMsg_thenVerifyOutput() throws Exception {
        config = new TbRenameKeysNodeConfiguration().defaultConfiguration();
        config.setRenameKeysMapping(Map.of("TestKey_1", "Attribute_1", "TestKey_2", "Attribute_2"));
        config.setFromMetadata(true);
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        node.init(ctx, nodeConfiguration);

        String data = "{\"Temperature_1\":22.5,\"TestKey_2\":10.3}";
        node.onMsg(ctx, getTbMsg(deviceId, data));

        ArgumentCaptor<TbMsg> newMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        verify(ctx, times(1)).tellSuccess(newMsgCaptor.capture());
        verify(ctx, never()).tellFailure(any(), any());

        TbMsg newMsg = newMsgCaptor.getValue();
        assertThat(newMsg).isNotNull();

        Map<String, String> mdDataMap = newMsg.getMetaData().getData();
        assertThat(mdDataMap.containsKey("Attribute_1")).isEqualTo(true);
    }

    @Test
    void givenEmptyKeys_whenOnMsg_thenVerifyOutput() throws Exception {
        TbRenameKeysNodeConfiguration defaultConfig = new TbRenameKeysNodeConfiguration().defaultConfiguration();
        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(defaultConfig));
        node.init(ctx, nodeConfiguration);

        String data = "{\"Temperature_1\":22.5,\"TestKey_2\":10.3}";
        TbMsg msg = getTbMsg(deviceId, data);
        node.onMsg(ctx, msg);

        ArgumentCaptor<TbMsg> newMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        verify(ctx, times(1)).tellSuccess(newMsgCaptor.capture());
        verify(ctx, never()).tellFailure(any(), any());

        TbMsg newMsg = newMsgCaptor.getValue();
        assertThat(newMsg).isNotNull();

        assertThat(newMsg.getMetaData()).isEqualTo(msg.getMetaData());
    }

    @Test
    void givenMsgDataNotJSONObject_whenOnMsg_thenVerifyOutput() throws Exception {
        String data = "[]";
        TbMsg msg = getTbMsg(deviceId, data);
        node.onMsg(ctx, msg);

        ArgumentCaptor<TbMsg> newMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        verify(ctx, times(1)).tellSuccess(newMsgCaptor.capture());
        verify(ctx, never()).tellFailure(any(), any());

        TbMsg newMsg = newMsgCaptor.getValue();
        assertThat(newMsg).isNotNull();

        assertThat(newMsg).isSameAs(msg);
    }

    private TbMsg getTbMsg(EntityId entityId, String data) {
        final Map<String, String> mdMap = Map.of(
                "TestKey_1", "Test",
                "country", "US",
                "city", "NY"
        );
        return TbMsg.newMsg("POST_ATTRIBUTES_REQUEST", entityId, new TbMsgMetaData(mdMap), data, callback);
    }
}
