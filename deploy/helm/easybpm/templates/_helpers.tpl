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
