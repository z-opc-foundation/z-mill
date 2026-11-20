package com.zifang.mill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 批量研磨：目录进、成品出。逐文件隔离失败，输出目录镜像相对路径，产出汇总报告。 */
public final class MillBatch {

    /** 一次批量研磨的台账。details 一行一个文件：ok / unsupported / failed 前缀。 */
    public record Report(int total, int ok, int unsupported, int failed, List<String> details) {
    }

    private MillBatch() {
    }

    public static Report grindAll(Mill mill, Path inputDir, Path outDir, List<MillView> views) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(inputDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));

        int ok = 0;
        int unsupported = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Path file : files) {
            Path relative = inputDir.relativize(file).normalize();
            Path target = outDir.resolve(relative);
            String name = file.getFileName().toString();
            String base = stripExtension(name);
            byte[] data = Files.readAllBytes(file);
            if (!mill.supports(data)) {
                unsupported++;
                details.add("unsupported " + relative);
                continue;
            }
            try {
                MillOutput output = mill.grind(data, name);
                Files.createDirectories(target.getParent());
                for (MillView view : views) {
                    Files.writeString(target.resolveSibling(base + "." + view.extension()),
                            view.render(output));
                }
                ok++;
                details.add("ok          " + relative);
            } catch (IOException | RuntimeException e) {
                failed++;
                details.add("failed      " + relative + ": " + e.getMessage());
            }
        }
        return new Report(files.size(), ok, unsupported, failed, List.copyOf(details));
    }

    /** 去掉最后一个扩展名；无扩展名原样返回。 */
    public static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
