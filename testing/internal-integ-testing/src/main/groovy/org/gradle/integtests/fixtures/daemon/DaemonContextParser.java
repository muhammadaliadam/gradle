/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures.daemon;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DefaultDaemonContext;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DaemonContextParser {
    @Nullable
    public static DaemonContext parseFromFile(DaemonLogFile log, GradleVersion version) {
        try (Stream<String> lines = log.lines()) {
            return lines.map(line -> parseFrom(line, version)).filter(Objects::nonNull).findFirst().orElse(null);
        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("unable to parse DefaultDaemonContext from source: [" + log.getFile().getAbsolutePath() + "].", e);
        }
    }

    public static DaemonContext parseFromString(String source, GradleVersion version) {
        DaemonContext context = parseFrom(source, version);
        if (context == null) {
            throw new IllegalStateException("unable to parse DefaultDaemonContext from source: [" + source + "].");
        }
        return context;
    }

    @Nullable
    private static DaemonContext parseFrom(String source, GradleVersion version) {
        if (version.getBaseVersion().compareTo(GradleVersion.version("8.7")) <= 0) {
            return parseFrom87(source);
        }
        Pattern pattern = Pattern.compile("^.*DefaultDaemonContext\\[(uid=[^\\n,]+)?,?javaHome=([^\\n]+),javaVersion=([^\\n]+),javaVendor=([^\\n]+),daemonRegistryDir=([^\\n]+),pid=([^\\n]+),idleTimeout=(.+?)(,priority=[^\\n,]+)?(?:,applyInstrumentationAgent=([^\\n,]+))?(?:,nativeServicesMode=([^\\n,]+))?,daemonOpts=([^\\n]+)].*",
                Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = pattern.matcher(source);

        if (matcher.matches()) {
            String uid = matcher.group(1) == null ? null : matcher.group(1).substring("uid=".length());
            String javaHome = matcher.group(2);
            JavaLanguageVersion javaVersion = JavaLanguageVersion.of(matcher.group(3));
            JvmVendor.KnownJvmVendor javaVendor = JvmVendor.KnownJvmVendor.parse(matcher.group(4));
            String daemonRegistryDir = matcher.group(5);
            String pidStr = matcher.group(6);
            Long pid = pidStr.equals("null") ? null : Long.parseLong(pidStr);
            Integer idleTimeout = Integer.decode(matcher.group(7));
            DaemonParameters.Priority priority = matcher.group(8) == null ? DaemonParameters.Priority.NORMAL : DaemonParameters.Priority.valueOf(matcher.group(8).substring(",priority=".length()));
            boolean applyInstrumentationAgent = Boolean.parseBoolean(matcher.group(9));
            NativeServicesMode nativeServicesMode = matcher.group(10) == null ? NativeServicesMode.ENABLED : NativeServicesMode.valueOf(matcher.group(10));
            List<String> jvmOpts = Lists.newArrayList(Splitter.on(',').split(matcher.group(11)));
            return new DefaultDaemonContext(uid, new File(javaHome), javaVersion, javaVendor, new File(daemonRegistryDir), pid, idleTimeout, jvmOpts, applyInstrumentationAgent, nativeServicesMode, priority);
        } else {
            return null;
        }
    }

    @Nullable
    private static DaemonContext parseFrom87(String source) {
        Pattern pattern = Pattern.compile("^.*DefaultDaemonContext\\[(uid=[^\\n,]+)?,?javaHome=([^\\n]+),daemonRegistryDir=([^\\n]+),pid=([^\\n]+),idleTimeout=(.+?)(,priority=[^\\n,]+)?(?:,applyInstrumentationAgent=([^\\n,]+))?(?:,nativeServicesMode=([^\\n,]+))?,daemonOpts=([^\\n]+)].*",
            Pattern.MULTILINE + Pattern.DOTALL);
        Matcher matcher = pattern.matcher(source);

        if (matcher.matches()) {
            String uid = matcher.group(1) == null ? null : matcher.group(1).substring("uid=".length());
            String javaHome = matcher.group(2);
            String daemonRegistryDir = matcher.group(3);
            String pidStr = matcher.group(4);
            Long pid = pidStr.equals("null") ? null : Long.parseLong(pidStr);
            Integer idleTimeout = Integer.decode(matcher.group(5));
            DaemonParameters.Priority priority = matcher.group(6) == null ? DaemonParameters.Priority.NORMAL : DaemonParameters.Priority.valueOf(matcher.group(6).substring(",priority=".length()));
            boolean applyInstrumentationAgent = Boolean.parseBoolean(matcher.group(7));
            NativeServicesMode nativeServicesMode = matcher.group(8) == null ? NativeServicesMode.ENABLED : NativeServicesMode.valueOf(matcher.group(8));
            List<String> jvmOpts = Lists.newArrayList(Splitter.on(',').split(matcher.group(9)));
            return new DefaultDaemonContext(uid, new File(javaHome), JavaLanguageVersion.of(8), JvmVendor.KnownJvmVendor.UNKNOWN, new File(daemonRegistryDir), pid, idleTimeout, jvmOpts, applyInstrumentationAgent, nativeServicesMode, priority);
        } else {
            return null;
        }
    }
}
