package io.vertx.grpc.plugin.generation;

import io.vertx.grpc.plugin.generation.generators.CodeGenerator;
import io.vertx.grpc.plugin.template.MustacheTemplateEngine;
import io.vertx.grpc.plugin.template.TemplateEngine;
import io.vertx.grpc.plugin.writer.FileWriter;

import java.util.*;

/**
 * The CodeGenerationEngine class is responsible for orchestrating the code generation process using registered generators, managing generation results, and handling file
 * persistence. It supports multiple types of generation defined by {@link GenerationType}. This engine allows for flexibility in registering and prioritizing specific generators.
 */
public class CodeGenerationEngine {

  private final Map<GenerationType, List<CodeGenerator>> generators;
  private final FileWriter fileWriter;

  public CodeGenerationEngine(Map<GenerationType, List<CodeGenerator>> generators, FileWriter fileWriter) {
    this.generators = generators;
    this.fileWriter = fileWriter;
  }

  public static CodeGenerationEngine.Builder builder() {
    return new CodeGenerationEngine.Builder();
  }

  /**
   * Generates code or other artifacts based on the provided generation context and writes the generated files to the output directory. If errors occur during the generation
   * process or while writing the files, they are captured in the resulting {@link GenerationResult}.
   *
   * @param context the context containing information required for code generation, including the package name, output directory, and any additional metadata
   * @return a {@link GenerationResult} object that contains the result of the generation process, including the generated files if successful, or a list of errors if any issues
   * occurred
   */
  public GenerationResult generate(GenerationContext context) {
    List<GeneratedFile> allFiles = new ArrayList<>();
    List<GenerationError> errors = new ArrayList<>();

    for (GenerationType type : getRequestedTypes(context)) {
      try {
        CodeGenerator generator = getGenerator(type);
        if (generator != null) {
          GenerationResult result = generator.generate(context);
          if (result.isSuccess()) {
            allFiles.addAll(result.getFiles());
          } else {
            errors.addAll(result.getErrors());
          }
        } else {
          errors.add(new GenerationError("No generator found for type: " + type, null, type));
        }
      } catch (Exception e) {
        errors.add(new GenerationError("Generation failed for type: " + type, e, type));
      }
    }

    if (!errors.isEmpty()) {
      return GenerationResult.failure(errors);
    }

    // Write files
    try {
      fileWriter.writeFiles(allFiles, context.getOutputDirectory());
      return GenerationResult.success(allFiles);
    } catch (Exception e) {
      errors.add(new GenerationError("Failed to write files", e, null));
      return GenerationResult.failure(errors);
    }
  }

  private List<GenerationType> getRequestedTypes(GenerationContext context) {
    if (context != null) {
      return determineGrpcTypes(context.getOptions());
    }
    return Collections.emptyList();
  }

  private List<GenerationType> determineGrpcTypes(GenerationOptions options) {
    List<GenerationType> types = new ArrayList<>();

    if (options.isGenerateClient() || options.isGenerateService()) {
      types.add(GenerationType.CONTRACT);
    }
    if (options.isGenerateClient()) {
      types.add(GenerationType.CLIENT);
      types.add(GenerationType.GRPC_CLIENT);
    }
    if (options.isGenerateService()) {
      types.add(GenerationType.SERVICE);
      types.add(GenerationType.GRPC_SERVICE);
    }
    if (options.isGenerateIo()) {
      types.add(GenerationType.GRPC_IO);
    }

    return types;
  }

  public CodeGenerator getGenerator(GenerationType type) {
    List<CodeGenerator> typeGenerators = generators.get(type);
    if (typeGenerators != null && !typeGenerators.isEmpty()) {
      return typeGenerators.get(0);
    }
    return null;
  }

  public static class Builder {

    private final Map<GenerationType, List<CodeGenerator>> generators = new HashMap<>();

    private TemplateEngine templateEngine;
    private FileWriter fileWriter = new FileWriter();

    public CodeGenerationEngine.Builder templateEngine(TemplateEngine templateEngine) {
      this.templateEngine = templateEngine;
      return this;
    }

    public CodeGenerationEngine.Builder fileWriter(FileWriter fileWriter) {
      this.fileWriter = fileWriter;
      return this;
    }

    public CodeGenerationEngine.Builder registerGenerator(GenerationType type, CodeGenerator generator) {
      generators.computeIfAbsent(type, k -> new ArrayList<>()).add(generator);
      return this;
    }

    public CodeGenerationEngine build() {
      if (templateEngine == null) {
        templateEngine = new MustacheTemplateEngine();
      }
      return new CodeGenerationEngine(generators, fileWriter);
    }
  }
}
