/*
 * Copyright © 2025 Jemin Huh (hjm1980@gmail.com)
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
package org.springaicommunity.playground.service.tool;

import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ToolManifest(
        String specVersion,
        Code code,
        Params params,
        List<StaticVariable> staticVariables,
        Runtime runtime,
        Sandbox sandbox,
        Capabilities capabilities,
        HumanInTheLoop humanInTheLoop,
        List<TestCase> tests,
        Audit audit,
        Integrity integrity,
        Signature signature) {

    public record Code(String lang, String source, String sha256, EntryPoint entryPoint,
                       ReturnContract returnContract) {
        public enum EntryPoint {ASYNC_IIFE, FUNCTION, MODULE}

        public enum ReturnContract {IIFE_RETURN, EXPORT_DEFAULT}
    }

    public record Params(Binding binding) {
        public enum Binding {GLOBALS, SINGLE_ARG_OBJECT}
    }

    public record StaticVariable(String name, Kind kind, String value, String envName) {
        public enum Kind {LITERAL, ENV}
    }

    public record Runtime(String id, String minVersion, String ecmaVersion, boolean javaInterop,
                          List<String> helpers) {}

    public record Sandbox(RiskLevel level, String profile, Overrides overrides) {
        public enum RiskLevel {L0, L1, L2, L3, L4, L5}

        public record Overrides(Set<String> denyClasses, NetworkPolicy network) {}
    }

    public record Capabilities(Network network, List<String> workspace, List<String> env, boolean sideEffect) {
        public record Network(List<String> outbound, List<String> outboundDeny) {}
    }

    public record HumanInTheLoop(Mode mode, String promptTemplate) {
        public enum Mode {DISABLED, REQUIRED}
    }

    public record TestCase(String name, Map<String, Object> input, Map<String, Object> assertSpec) {}

    public record Audit(int schemaVersion, Instant lastTestedAt, String lastTestedHash, boolean passing) {}

    public record Integrity(String manifestHash, String codeHash, String descriptionHash) {}

    public record Signature(String algo, String publicKey, String value) {}
}
