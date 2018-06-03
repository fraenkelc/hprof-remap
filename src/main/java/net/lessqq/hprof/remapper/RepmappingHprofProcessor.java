/*
 * Copyright 2018 Christian Fränkel
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
package net.lessqq.hprof.remapper;

import com.badoo.hprof.library.HprofReader;
import com.badoo.hprof.library.HprofWriter;
import com.badoo.hprof.library.Tag;
import com.badoo.hprof.library.model.HprofString;
import com.badoo.hprof.library.processor.CopyProcessor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class RepmappingHprofProcessor extends CopyProcessor {
    private final Map<String, String> methodMapping;
    private final Map<String, String> fieldMapping;
    private final HprofWriter writer;

    public RepmappingHprofProcessor(OutputStream output, Map<String, String> methodMapping, Map<String, String> fieldMapping) {
        super(output);
        this.methodMapping = methodMapping;
        this.fieldMapping = fieldMapping;
        this.writer = new HprofWriter(output);
    }

    @Override
    public void onRecord(int tag, int timestamp, int length, @Nonnull HprofReader reader) throws IOException {
        if (tag == Tag.STRING) {
            HprofString string = reader.readStringRecord(length, timestamp);
            string.setValue(methodMapping.getOrDefault(string.getValue(), string.getValue()));
            string.setValue(fieldMapping.getOrDefault(string.getValue(), string.getValue()));
            writer.writeStringRecord(string);
        } else
            super.onRecord(tag, timestamp, length, reader);
    }

}
