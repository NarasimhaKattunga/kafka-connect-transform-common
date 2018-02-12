package com.github.jcustenborder.kafka.connect.transform.common;

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import com.github.jcustenborder.kafka.connect.utils.config.Title;
import com.google.common.base.Strings;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class ExtractNestedField<R extends ConnectRecord<R>> extends BaseTransformation<R> {
  private static final Logger log = LoggerFactory.getLogger(ExtractNestedField.class);


  @Override
  public ConfigDef config() {
    return ExtractNestedFieldConfig.config();
  }

  @Override
  public void close() {

  }

  ExtractNestedFieldConfig config;
  Map<Schema, Schema> schemaCache;

  @Override
  public void configure(Map<String, ?> map) {
    this.config = new ExtractNestedFieldConfig(map);
    this.schemaCache = new HashMap<>();
  }


  @Override
  protected SchemaAndValue processStruct(R record, SchemaAndValue schemaAndValue) {
    final Schema inputSchema = schemaAndValue.schema();
    final Struct inputStruct = (Struct) schemaAndValue.value();
    final Struct innerStruct = inputStruct.getStruct(this.config.outerFieldName);
    final Schema outputSchema = this.schemaCache.computeIfAbsent(inputSchema, s -> {

      final Field innerField = innerStruct.schema().field(this.config.innerFieldName);
      final SchemaBuilder builder = SchemaBuilder.struct();
      if (!Strings.isNullOrEmpty(inputSchema.name())) {
        builder.name(inputSchema.name());
      }
      if (inputSchema.isOptional()) {
        builder.optional();
      }
      for (Field inputField : inputSchema.fields()) {
        builder.field(inputField.name(), inputField.schema());
      }
      builder.field(this.config.innerFieldName, innerField.schema());
      return builder.build();
    });
    final Struct outputStruct = new Struct(outputSchema);
    for (Field inputField : inputSchema.fields()) {
      final Object value = inputStruct.get(inputField);
      outputStruct.put(inputField.name(), value);
    }
    final Object innerFieldValue = innerStruct.get(this.config.innerFieldName);
    outputStruct.put(this.config.innerFieldName, innerFieldValue);

    return new SchemaAndValue(outputSchema, outputStruct);
  }

  @Override
  protected SchemaAndValue processMap(R record, SchemaAndValue schemaAndValue) {
    throw new UnsupportedOperationException();
  }

  @Title("ExtractNestedField(Key)")
  @Description("This transformation is used to extract a field from a nested struct and append it " +
      "to the parent struct.")
  @DocumentationTip("This transformation is used to manipulate fields in the Key of the record.")
  public static class Key<R extends ConnectRecord<R>> extends ExtractNestedField<R> {

    @Override
    public R apply(R r) {
      final SchemaAndValue transformed = process(r, new SchemaAndValue(r.keySchema(), r.key()));

      return r.newRecord(
          r.topic(),
          r.kafkaPartition(),
          transformed.schema(),
          transformed.value(),
          r.valueSchema(),
          r.value(),
          r.timestamp()
      );
    }
  }

  @Title("ExtractNestedField(Value)")
  @Description("This transformation is used to extract a field from a nested struct and append it " +
      "to the parent struct.")
  public static class Value<R extends ConnectRecord<R>> extends ExtractNestedField<R> {

    @Override
    public R apply(R r) {
      final SchemaAndValue transformed = process(r, new SchemaAndValue(r.valueSchema(), r.value()));

      return r.newRecord(
          r.topic(),
          r.kafkaPartition(),
          r.keySchema(),
          r.key(),
          transformed.schema(),
          transformed.value(),
          r.timestamp()
      );
    }
  }

}