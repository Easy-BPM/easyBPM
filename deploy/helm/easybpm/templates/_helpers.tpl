{{- define "easybpm.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "easybpm.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "easybpm.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "easybpm.labels" -}}
app.kubernetes.io/name: {{ include "easybpm.name" . }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "easybpm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "easybpm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "easybpm.secretName" -}}
{{- if .Values.existingSecretName -}}
{{- .Values.existingSecretName -}}
{{- else -}}
{{- printf "%s-secrets" (include "easybpm.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "easybpm.image" -}}
{{- printf "%s/%s:%s" .Values.global.imageRegistry .image .Values.global.imageTag -}}
{{- end -}}

{{- define "easybpm.licenseSecretKey" -}}
{{- default "EASYBPM_LICENSE_KEY" .Values.license.secretKey -}}
{{- end -}}

{{- define "easybpm.validateLicense" -}}
{{- $licenseSecretKey := include "easybpm.licenseSecretKey" . -}}
{{- if not $licenseSecretKey -}}
{{- fail "license.secretKey is required and must name the Secret key that stores the Easy BPM license." -}}
{{- end -}}
{{- if and .Values.secrets.create (not .Values.existingSecretName) -}}
{{- $licenseKey := required "license.key is required when Helm creates the Easy BPM Secret. Set --set license.key=<valid-license> or use existingSecretName with a Secret that contains EASYBPM_LICENSE_KEY." .Values.license.key -}}
{{- if or (eq $licenseKey "change-me") (eq $licenseKey "replace-with-valid-license-key") -}}
{{- fail "license.key must be a valid Easy BPM license, not a placeholder value." -}}
{{- end -}}
{{- end -}}
{{- end -}}
