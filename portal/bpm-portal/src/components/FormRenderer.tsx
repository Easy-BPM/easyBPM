import React from 'react';
import Form from '@rjsf/bootstrap-4';  // ou '@rjsf/bootstrap-5' quando existir
import validator from '@rjsf/validator-ajv8';
import { JSONSchema7 } from 'json-schema';

interface Props {
  schema: JSONSchema7;
  onSubmit: (formData: any) => void;
}

const FormRenderer: React.FC<Props> = ({ schema, onSubmit }) => {
  return (
    <Form
      schema={schema}
      onSubmit={({ formData }) => onSubmit(formData)}
      validator={validator}
    />
  );
};

export default FormRenderer;
