# Backend Form Schemas

These files use the backend deploy format:

```json
{
  "formId": "qaIntakeForm",
  "name": "QA Intake Form",
  "schema": {
    "title": "QA Intake Form",
    "type": "object",
    "properties": {}
  }
}
```

Use these with the backend `POST /forms` endpoint or the Form Modeler deploy action after recreating/importing the editable form.

Do not use these files with the Modeler form import button. For Modeler import, use the files in `../forms-modeler/`, which include the required top-level `form` object.
