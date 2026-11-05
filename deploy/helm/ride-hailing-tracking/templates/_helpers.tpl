{{- define "rht.name" -}}ride-hailing-tracking{{- end -}}

{{- define "rht.fullname" -}}
{{- if contains (include "rht.name" .) .Release.Name -}}{{ .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else -}}{{ printf "%s-%s" .Release.Name (include "rht.name" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- end -}}

{{- define "rht.labels" -}}
app: {{ include "rht.name" . }}
app.kubernetes.io/name: {{ include "rht.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "rht.selectorLabels" -}}
app: {{ include "rht.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "rht.secretName" -}}
{{- if .Values.secret.create -}}{{ include "rht.fullname" . }}{{- else -}}{{ required "secret.existingSecret is required when secret.create=false" .Values.secret.existingSecret }}{{- end -}}
{{- end -}}
