package com.stanfy.helium.internal.entities;

import com.stanfy.helium.format.FormatReader;
import com.stanfy.helium.format.FormatWriter;
import com.stanfy.helium.model.Field;
import com.stanfy.helium.model.Message;
import com.stanfy.helium.model.Sequence;
import com.stanfy.helium.model.Type;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for message serializers.
 */
final class MessageConverter extends BaseConverter<Message> {

  MessageConverter(final Message type) {
    super(type);
  }

  private String typeHierarchy() {
    StringBuilder sb = new StringBuilder();
    Message t = type;
    sb.append(t.getName());
    while (t.getParent() != null) {
      sb.append("->");
      sb.append(t.getParent().getName());
      t = t.getParent();
    }
    return sb.toString();
  }

  @Override
  public void writeData(final FormatWriter output, final Object value) throws IOException {
    @SuppressWarnings("unchecked")
    Map<String, Object> values = (Map<String, Object>) value;

    output.beginMessage(type);
    Message msg = getType();

    while (msg != null) {
      for (Field f : msg.getActiveFields()) {
        Object v = values.get(f.getName());
        writeField(f, v, output);
      }
      msg = msg.getParent();
    }

    output.endMessage(this.type);
  }

  private void writeField(final Field field, final Object value, final FormatWriter out) throws IOException {
    if (value == null) {
      if (field.isRequired()) {
        throw new IllegalArgumentException("Field " + field.getName() + " in " + typeHierarchy() + " is required. But null is provided");
      }
      return;
    }
    out.beginMessageField(field);
    Type valueType = field.isSequence() ? fieldSequence(field) : field.getType();
    getConverter(valueType).write(out, value);
    out.endMessageField(field);
  }

  private Sequence fieldSequence(Field field) {
    Sequence s = new Sequence();
    s.setName(type.getCanonicalName() + "_" + field.getCanonicalName() + "_sec");
    s.setItemsType(field.getType());
    return s;
  }

  @Override
  public Map<String, ?> readData(final FormatReader input, final List<ValidationError> errors) throws IOException {
    LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
    Set<String> visitedFields = new HashSet<String>();
    input.beginMessage(type);
    while (input.hasNext()) {
      String fieldName = input.nextFieldName(type);
      Field field = type.fieldByNameWithParents(fieldName);

      if (field == null) {
        if (!type.isSkipUnknownFields()) {
          errors.add(new ValidationError("Unexpected field '" + fieldName + "' in " + typeHierarchy()));
        }
        input.skipValue();
        continue;
      }

      visitedFields.add(fieldName);

      Type fieldType = field.getType();
      if (fieldType instanceof Sequence) {
        throw new IllegalStateException("Sequences are accepted as roots only!");
      }

      if (field.isSkip()) {
        input.skipValue();
        continue;
      }

      if (input.checkNextNull()) {
        input.skipValue();
        if (field.isRequired()) {
          errors.add(new ValidationError(type, field, "field is required but got NULL"));
        }
        continue;
      }

      LinkedList<ValidationError> childrenErrors = new LinkedList<>();
      Type valueType = field.isSequence() ? fieldSequence(field) : field.getType();
      Object fieldValue = getConverter(valueType).read(input, childrenErrors);
      if (fieldValue != null) {
        values.put(fieldName, fieldValue);
      }

      if (!childrenErrors.isEmpty()) {
        final String message;
        if (field.isSequence()) {
          message = "array contains errors";
        } else if (!fieldType.isPrimitive()) {
          message = "object contains errors";
        } else {
          message = "value format errors";
        }
        ValidationError error = new ValidationError(type, field, message);
        error.setChildren(childrenErrors);
        errors.add(error);
      }

    }

    for (Field requiredField : type.getRequiredFields()) {
      if (!visitedFields.contains(requiredField.getName())) {
        errors.add(new ValidationError(type, requiredField, "field is not provided"));
      }
    }

    input.endMessage(type);

    return values;
  }

}
