package com.zifang.mill.cli;

import com.zifang.mill.Mill;
import com.zifang.mill.MillBatch;
import com.zifang.mill.MillOutput;
import com.zifang.mill.MillView;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** z-mill 命令行：z-mill &lt;文件|目录&gt; [-o 输出目录] [-f md,txt,html,json] */
public final class Main {

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 1) {
            err.println("用法: z-mill <文件|目录> [-o 输出目录] [-f md,txt,html,json]");
            return 1;
        }
        Path input = Path.of(args[0]);
        Path outDir = null;
        List<MillView> views = List.of(MillView.MARKDOWN);
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                err.println("参数缺值: " + args[i]);
                return 1;
            }
            switch (args[i]) {
                case "-o" -> outDir = Path.of(args[i + 1]);
                case "-f" -> {
                    List<MillView> parsed = new ArrayList<>();
                    for (String token : args[i + 1].split(",")) {
                        try {
                            parsed.add(MillView.parse(token.trim()));
                        } catch (IllegalArgumentException e) {
                            err.println(e.getMessage());
                            return 1;
                        }
                    }
                    views = parsed;
                }
                default -> {
                    err.println("未知参数: " + args[i]);
                    return 1;
                }
            }
        }
        if (!Files.exists(input)) {
            err.println("不存在: " + input);
            return 1;
        }
        Mill mill = new Mill();
        try {
            if (Files.isDirectory(input)) {
                return grindDirectory(mill, input, outDir, views, out);
            }
            return grindFile(mill, input, outDir, views, out, err);
        } catch (IOException | RuntimeException e) {
            err.println("研磨失败: " + e.getMessage());
            return 2;
        }
    }

    private static int grindDirectory(Mill mill, Path input, Path outDir, List<MillView> views,
                                      PrintStream out) throws IOException {
        Path destination = outDir == null ? input.resolve("z-mill-out") : outDir;
        MillBatch.Report report = MillBatch.grindAll(mill, input, destination, views);
        report.details().forEach(out::println);
        out.printf("共 %d 个文件：成功 %d，不支持 %d，失败 %d；成品在 %s%n",
                report.total(), report.ok(), report.unsupported(), report.failed(), destination);
        return report.failed() > 0 ? 2 : 0;
    }

    private static int grindFile(Mill mill, Path input, Path outDir, List<MillView> views,
                                 PrintStream out, PrintStream err) throws IOException {
        Path destination = outDir == null ? input.toAbsolutePath().getParent() : outDir;
        Files.createDirectories(destination);
        byte[] data = Files.readAllBytes(input);
        if (!mill.supports(data)) {
            err.println("不支持的格式: " + input);
            return 2;
        }
        MillOutput output = mill.grind(data, input.getFileName().toString());
        String base = MillBatch.stripExtension(input.getFileName().toString());
        for (MillView view : views) {
            Path file = destination.resolve(base + "." + view.extension());
            Files.writeString(file, view.render(output));
            out.println("ok          " + file);
        }
        return 0;
    }
}
