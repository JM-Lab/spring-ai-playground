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
package org.springaicommunity.playground.webui.tool;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.service.tool.ChipListBinding;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.policy.SandboxPostureCalculator;
import org.springaicommunity.playground.service.tool.policy.SandboxPostureCalculator.Inputs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class SandboxCapabilitiesView extends Details {

    private final SandboxPostureCalculator postureCalculator;

    private final RadioButtonGroup<String> networkModeField;
    private final MultiSelectComboBox<String> hostsField;
    private final RadioButtonGroup<String> fileModeField;
    private final TextField fsBasePathField;
    private final MultiSelectComboBox<String> allowClassesField;
    private final MultiSelectComboBox<String> denyClassesField;
    private final Span postureBadge;
    private final Icon postureIcon;
    private final Set<String> yamlBaselineDeny;
    private final Set<String> yamlBaselineAllow;

    public SandboxCapabilitiesView(SpringAiPlaygroundOptions options, SandboxPostureCalculator postureCalculator) {
        this.postureCalculator = postureCalculator;

        JsSandbox baseline = options != null && options.toolStudio() != null
                ? options.toolStudio().jsSandbox() : null;
        this.yamlBaselineAllow = baseline != null && baseline.allowClasses() != null
                ? baseline.allowClasses() : Set.of();
        this.yamlBaselineDeny = baseline != null && baseline.denyClasses() != null
                ? baseline.denyClasses() : Set.of();

        this.allowClassesField = chipListField("Allow Classes",
                "Java APIs this tool can use. Removing tightens. Adding a new class can raise risk (L3+).",
                this.yamlBaselineAllow);

        this.denyClassesField = chipListField("Deny Classes",
                "Always blocked. Removing reflection denies → L4. Removing System / Runtime / Process → L5.",
                this.yamlBaselineDeny);

        this.networkModeField = new RadioButtonGroup<>();
        this.networkModeField.setLabel("Network mode (fetch)");
        this.networkModeField.setItems("blocked", "allowlist", "strict", "open");
        this.networkModeField.setValue("blocked");
        this.networkModeField.setHelperText(
                "blocked = no outbound · allowlist = listed hosts only (L3) · "
                        + "strict = any host with SSRF 4-layer guard (L3) · open = any host (L4)");

        this.hostsField = new MultiSelectComboBox<>("Allowed hosts");
        this.hostsField.setAllowCustomValue(true);
        this.hostsField.setWidthFull();
        this.hostsField.addCustomValueSetListener(e -> {
            Set<String> next = new LinkedHashSet<>(this.hostsField.getValue());
            next.add(e.getDetail().trim());
            this.hostsField.setValue(next);
        });

        this.networkModeField.addValueChangeListener(e -> {
            this.hostsField.setVisible("allowlist".equals(e.getValue()));
            updatePostureBadge();
        });
        this.hostsField.addValueChangeListener(e -> updatePostureBadge());
        this.allowClassesField.addValueChangeListener(e -> updatePostureBadge());
        this.denyClassesField.addValueChangeListener(e -> updatePostureBadge());
        this.hostsField.setVisible(false);

        this.fileModeField = new RadioButtonGroup<>();
        this.fileModeField.setLabel("Filesystem mode (safety.fs)");
        this.fileModeField.setItems("off", "read", "read+write");
        this.fileModeField.setValue("off");
        this.fileModeField.setHelperText(
                "off = hidden · read = read-only (L3) · read+write = read and write (L4)");

        String defaultBasePath = options != null && options.toolStudio() != null
                && options.toolStudio().fs() != null
                && options.toolStudio().fs().basePath() != null
                ? options.toolStudio().fs().basePath()
                : System.getProperty("user.home");
        this.fsBasePathField = new TextField("Base path");
        this.fsBasePathField.setPlaceholder(defaultBasePath);
        this.fsBasePathField.setAutocomplete(Autocomplete.ON);
        this.fsBasePathField.setWidthFull();

        Button useDefaultBtn = new Button(VaadinIcon.REFRESH.create());
        useDefaultBtn.getElement().setProperty("title", "Use default");
        useDefaultBtn.addThemeName("icon small");
        useDefaultBtn.addClickListener(e -> this.fsBasePathField.clear());

        Button browseBtn = new Button(VaadinIcon.FOLDER_OPEN.create());
        browseBtn.getElement().setProperty("title", "Browse for directory");
        browseBtn.addThemeName("icon small primary");
        browseBtn.addClickListener(e -> openDirectoryBrowser(defaultBasePath));

        this.fsBasePathField.setReadOnly(true);
        useDefaultBtn.setEnabled(false);
        browseBtn.setEnabled(false);
        applyBasePathHelperText("off", defaultBasePath);

        Div basePathSuffix = new Div(useDefaultBtn, browseBtn);
        basePathSuffix.getStyle()
                .set("display", "flex")
                .set("gap", "var(--lumo-space-xs)");
        this.fsBasePathField.setSuffixComponent(basePathSuffix);

        this.fileModeField.addValueChangeListener(e -> {
            boolean active = !"off".equals(e.getValue());
            this.fsBasePathField.setReadOnly(!active);
            useDefaultBtn.setEnabled(active);
            browseBtn.setEnabled(active);
            applyBasePathHelperText(e.getValue(), defaultBasePath);
            updatePostureBadge();
        });

        VerticalLayout networkGroup = new VerticalLayout(this.networkModeField, this.hostsField);
        networkGroup.setPadding(false);
        networkGroup.setSpacing(false);
        networkGroup.setWidthFull();

        Div basePathSection = new Div(this.fsBasePathField);
        basePathSection.setWidthFull();
        basePathSection.getStyle()
                .set("margin-left", "var(--lumo-space-m)")
                .set("padding-left", "var(--lumo-space-s)")
                .set("border-left", "2px solid var(--lumo-contrast-10pct)")
                .set("box-sizing", "border-box");
        VerticalLayout fsGroup = new VerticalLayout(this.fileModeField, basePathSection);
        fsGroup.setPadding(false);
        fsGroup.setSpacing(false);
        fsGroup.setWidthFull();

        VerticalLayout body = new VerticalLayout(
                this.denyClassesField,
                this.allowClassesField,
                networkGroup,
                fsGroup);
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidthFull();
        body.getStyle().set("gap", "var(--lumo-space-s)");

        H5 title = new H5("Sandbox & Capabilities");
        title.getStyle().set("margin", "0");

        Span separator = new Span("·");
        separator.getStyle()
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("margin", "0 0.6em");

        this.postureIcon = VaadinIcon.LOCK.create();
        this.postureIcon.setSize("1.1em");

        this.postureBadge = new Span();
        this.postureBadge.getStyle().set("margin-inline-start", "0.35em");

        HorizontalLayout summary = new HorizontalLayout(title, separator, this.postureIcon, this.postureBadge);
        summary.setAlignItems(HorizontalLayout.Alignment.CENTER);
        summary.setSpacing(false);

        updatePostureBadge();

        setSummary(summary);
        setContent(body);
        setOpened(false);
        setWidthFull();
    }

    public ToolSpec.SandboxOverrides currentOverrides() {
        Set<String> userAllow = this.allowClassesField.getValue();
        Set<String> userDeny = this.denyClassesField.getValue();
        Set<String> addAllow = diff(userAllow, this.yamlBaselineAllow);
        Set<String> removeAllow = diff(this.yamlBaselineAllow, userAllow);
        Set<String> addDeny = diff(userDeny, this.yamlBaselineDeny);
        Set<String> removeDeny = diff(this.yamlBaselineDeny, userDeny);
        String networkMode = this.networkModeField.getValue();
        Set<String> hosts = this.hostsField.getValue();
        String fileMode = this.fileModeField.getValue();
        Boolean fileRead = fileMode == null ? null : !"off".equals(fileMode);
        Boolean fileWrite = fileMode == null ? null : "read+write".equals(fileMode);
        String fsBasePath = this.fsBasePathField.getValue();
        return new ToolSpec.SandboxOverrides(addAllow, removeAllow, addDeny, removeDeny, networkMode, hosts,
                fileRead, fileWrite, fsBasePath);
    }

    public void applyOverrides(ToolSpec.SandboxOverrides overrides) {
        if (overrides == null) return;
        Set<String> resolvedAllow = new LinkedHashSet<>(this.yamlBaselineAllow);
        if (overrides.removeAllowClasses() != null) resolvedAllow.removeAll(overrides.removeAllowClasses());
        if (overrides.addAllowClasses() != null) resolvedAllow.addAll(overrides.addAllowClasses());
        this.allowClassesField.setItems(resolvedAllow);
        this.allowClassesField.setValue(resolvedAllow);

        Set<String> resolvedDeny = new LinkedHashSet<>(this.yamlBaselineDeny);
        if (overrides.removeDenyClasses() != null) resolvedDeny.removeAll(overrides.removeDenyClasses());
        if (overrides.addDenyClasses() != null) resolvedDeny.addAll(overrides.addDenyClasses());
        this.denyClassesField.setItems(resolvedDeny);
        this.denyClassesField.setValue(resolvedDeny);

        if (overrides.networkMode() != null && !overrides.networkMode().isBlank()) {
            this.networkModeField.setValue(overrides.networkMode());
        }
        if (overrides.hostsAllow() != null) {
            Set<String> hosts = new LinkedHashSet<>(overrides.hostsAllow());
            this.hostsField.setItems(hosts);
            this.hostsField.setValue(hosts);
        }

        if (overrides.fileWrite() != null && overrides.fileWrite()) {
            this.fileModeField.setValue("read+write");
        } else if (overrides.fileRead() != null && overrides.fileRead()) {
            this.fileModeField.setValue("read");
        } else if (overrides.fileRead() != null || overrides.fileWrite() != null) {
            this.fileModeField.setValue("off");
        }
        if (overrides.fsBasePath() != null && !overrides.fsBasePath().isBlank()) {
            this.fsBasePathField.setValue(overrides.fsBasePath());
        }
        updatePostureBadge();
    }

    private void applyBasePathHelperText(String fileMode, String defaultBasePath) {
        String helper = "off".equals(fileMode)
                ? "Filesystem mode is off — set a path now and toggle to read/read+write to enable safety.fs.* (default: " + defaultBasePath + ")"
                : "Tools read/write inside this folder only. Default: " + defaultBasePath + ".";
        this.fsBasePathField.setHelperText(helper);
    }

    private static MultiSelectComboBox<String> chipListField(String label, String helper, Set<String> baseline) {
        MultiSelectComboBox<String> field = new MultiSelectComboBox<>(label);
        field.setHelperText(helper);
        field.setWidthFull();
        field.setAllowCustomValue(true);

        ChipListBinding binding = new ChipListBinding(baseline);
        field.setItems(binding.items());
        field.setValue(binding.selected());

        field.addCustomValueSetListener(e -> {
            if (binding.add(e.getDetail())) {
                field.setItems(binding.items());
                field.setValue(binding.selected());
            }
        });
        field.addValueChangeListener(e -> binding.replaceSelected(e.getValue()));
        return field;
    }

    private void updatePostureBadge() {
        if (this.postureBadge == null) return;
        RiskLevel level = computeRisk();
        this.postureBadge.setText(postureLabel(level, currentSignals()) + "  " + level.name());
        String theme = switch (level) {
            case L0, L1 -> "badge success primary small";
            case L2     -> "badge contrast small";
            case L3     -> "badge contrast primary small";
            case L4     -> "badge warning primary small";
            case L5     -> "badge error primary small";
        };
        this.postureBadge.getElement().setAttribute("theme", theme);
        VaadinIcon iconKind = switch (level) {
            case L0, L1, L2 -> VaadinIcon.LOCK;
            case L3         -> VaadinIcon.SHIELD;
            case L4         -> VaadinIcon.UNLOCK;
            case L5         -> VaadinIcon.WARNING;
        };
        String iconColour = switch (level) {
            case L0, L1 -> "var(--lumo-success-color)";
            case L2, L3 -> "var(--lumo-primary-color)";
            case L4     -> "#f0a500";
            case L5     -> "var(--lumo-error-color)";
        };
        this.postureIcon.getElement().setAttribute("icon", "vaadin:" + iconKind.name().toLowerCase().replace('_', '-'));
        this.postureIcon.getStyle().set("color", iconColour);
    }

    private RiskLevel computeRisk() {
        String mode = this.networkModeField.getValue();
        Set<String> hosts = this.hostsField.getValue();
        String fileMode = this.fileModeField.getValue();
        boolean fileRead = !"off".equals(fileMode);
        boolean fileWrite = "read+write".equals(fileMode);
        Set<String> userDeny = this.denyClassesField.getValue();
        Set<String> userAllow = this.allowClassesField.getValue();
        return this.postureCalculator.compute(
                new Inputs(mode, hosts, fileRead, fileWrite,
                        this.yamlBaselineDeny, userDeny, this.yamlBaselineAllow, userAllow));
    }

    private record PostureSignals(String networkMode, boolean fileRead, boolean fileWrite,
                                  boolean customClasses) {}

    private PostureSignals currentSignals() {
        String mode = this.networkModeField.getValue();
        String fileMode = this.fileModeField.getValue();
        boolean fileRead = !"off".equals(fileMode);
        boolean fileWrite = "read+write".equals(fileMode);
        boolean customClasses = !diff(this.allowClassesField.getValue(), this.yamlBaselineAllow).isEmpty()
                || !diff(this.denyClassesField.getValue(), this.yamlBaselineDeny).isEmpty();
        return new PostureSignals(mode, fileRead, fileWrite, customClasses);
    }

    static String postureLabel(RiskLevel level, PostureSignals signals) {
        return switch (level) {
            case L0 -> "Locked";
            case L1 -> "Locked + clock";
            case L2 -> signals != null && signals.fileRead() ? "File read" : "Workspace";
            case L3 -> {
                if (signals == null) yield "Sandboxed";
                if ("strict".equalsIgnoreCase(signals.networkMode())) yield "Network strict";
                if ("allowlist".equalsIgnoreCase(signals.networkMode())) yield "Network allowlist";
                if (signals.fileRead()) yield "File read";
                yield "Sandboxed";
            }
            case L4 -> {
                if (signals == null) yield "Open network";
                if (signals.fileWrite()) yield "File write";
                if ("open".equalsIgnoreCase(signals.networkMode())) yield "Open network";
                yield "Elevated";
            }
            case L5 -> "Side effects";
        };
    }

    private void openDirectoryBrowser(String defaultBasePath) {
        Path startPath;
        String current = this.fsBasePathField.getValue();
        if (current != null && !current.isBlank()) {
            startPath = Paths.get(current);
        } else {
            startPath = Paths.get(defaultBasePath);
        }
        if (!Files.isDirectory(startPath)) {
            startPath = Paths.get(System.getProperty("user.home"));
        }
        final Path rootPath = startPath.toAbsolutePath().normalize();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Select base directory");
        dialog.setWidth("700px");
        dialog.setHeight("520px");
        dialog.setResizable(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setCloseOnEsc(true);

        TreeGrid<Path> tree = new TreeGrid<>();
        tree.addHierarchyColumn(p -> p.equals(rootPath)
                ? p.toString()
                : (p.getFileName() != null ? p.getFileName().toString() : p.toString()))
                .setHeader("Folder").setAutoWidth(true);
        tree.setItems(List.of(rootPath), this::listChildDirectories);
        tree.setHeightFull();

        Span selectedLabel = new Span("Selected: " + rootPath);
        selectedLabel.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        tree.asSingleSelect().addValueChangeListener(ev -> {
            Path p = ev.getValue();
            selectedLabel.setText("Selected: " + (p != null ? p : "(none)"));
        });

        Button selectBtn = new Button("Use this directory", VaadinIcon.CHECK.create());
        selectBtn.getElement().setAttribute("theme", "primary");
        selectBtn.addClickListener(ev -> {
            Path p = tree.asSingleSelect().getValue();
            if (p == null) p = rootPath;
            this.fsBasePathField.setValue(p.toAbsolutePath().toString());
            dialog.close();
        });
        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        VerticalLayout content = new VerticalLayout(tree, selectedLabel);
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);
        dialog.getFooter().add(cancelBtn, selectBtn);
        dialog.open();
    }

    private Collection<Path> listChildDirectories(Path parent) {
        if (parent == null || !Files.isDirectory(parent)) return List.of();
        try (Stream<Path> stream = Files.list(parent)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a == null ? Set.of() : a);
        if (b != null) out.removeAll(b);
        return out;
    }
}
