package main

import (
	"context"
	"fmt"
	"html/template"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

type application struct {
	monitor *monitor
	store   *configStore
	mu      sync.Mutex
}

type reportPage struct {
	StartedAt      string
	SampleInterval string
	BucketSizeMiB  uint64
	Containers     []containerReport
}

type containerReport struct {
	Name          string
	EscapedName   string
	StartedAt     string
	LastSampleAt  string
	LastError     string
	LastErrorAt   string
	Count         uint64
	HasSamples    bool
	CurrentMiB    string
	MinimumMiB    string
	MaximumMiB    string
	AverageMiB    string
	HistogramRows []histogramReport
}

type histogramReport struct {
	Range      string
	Count      uint64
	Percentage string
}

func newApplication(monitor *monitor, store *configStore) *application {
	return &application{monitor: monitor, store: store}
}

func (application *application) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", application.handleReport)
	mux.HandleFunc("/health", application.handleHealth)
	mux.HandleFunc("/add", application.handleAdd)
	mux.HandleFunc("/add/", application.handleAdd)
	mux.HandleFunc("/del/", application.handleDelete)
	return mux
}

func (application *application) handleReport(response http.ResponseWriter, request *http.Request) {
	if request.URL.Path != "/" {
		http.NotFound(response, request)
		return
	}
	if request.Method != http.MethodGet {
		methodNotAllowed(response, http.MethodGet)
		return
	}

	response.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := reportTemplate.Execute(response, newReportPage(application.monitor.Snapshot())); err != nil {
		http.Error(response, "render report", http.StatusInternalServerError)
	}
}

func (application *application) handleHealth(response http.ResponseWriter, request *http.Request) {
	if request.Method != http.MethodGet {
		methodNotAllowed(response, http.MethodGet)
		return
	}
	response.Header().Set("Content-Type", "text/plain; charset=utf-8")
	response.WriteHeader(http.StatusOK)
	_, _ = response.Write([]byte("ok\n"))
}

func (application *application) handleAdd(response http.ResponseWriter, request *http.Request) {
	if request.Method != http.MethodPost {
		methodNotAllowed(response, http.MethodPost)
		return
	}

	containerName := strings.TrimPrefix(request.URL.Path, "/add/")
	if request.URL.Path == "/add" {
		containerName = request.FormValue("name")
	}
	containerName, err := url.PathUnescape(containerName)
	if err != nil {
		http.Error(response, "invalid container name", http.StatusBadRequest)
		return
	}
	containerName = strings.TrimSpace(containerName)

	if err := application.addContainer(containerName); err != nil {
		http.Error(response, err.Error(), http.StatusBadRequest)
		return
	}

	application.respondAfterMutation(response, request)
}

func (application *application) handleDelete(response http.ResponseWriter, request *http.Request) {
	if request.Method != http.MethodPost && request.Method != http.MethodDelete {
		response.Header().Set("Allow", http.MethodPost+", "+http.MethodDelete)
		http.Error(response, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
		return
	}

	containerName, err := url.PathUnescape(strings.TrimPrefix(request.URL.Path, "/del/"))
	if err != nil {
		http.Error(response, "invalid container name", http.StatusBadRequest)
		return
	}
	containerName = strings.TrimSpace(containerName)

	if err := application.deleteContainer(containerName); err != nil {
		http.Error(response, err.Error(), http.StatusBadRequest)
		return
	}

	application.respondAfterMutation(response, request)
}

func (application *application) addContainer(containerName string) error {
	if err := validateContainerName(containerName); err != nil {
		return err
	}

	application.mu.Lock()
	defer application.mu.Unlock()

	if application.monitor.Has(containerName) {
		return nil
	}

	containers := append(application.monitor.Names(), containerName)
	if err := application.store.Save(containers); err != nil {
		return err
	}
	application.monitor.Add(containerName)

	go application.monitor.Sample(context.Background(), containerName)
	return nil
}

func (application *application) deleteContainer(containerName string) error {
	if err := validateContainerName(containerName); err != nil {
		return err
	}

	application.mu.Lock()
	defer application.mu.Unlock()

	if !application.monitor.Has(containerName) {
		return nil
	}

	containers := application.monitor.Names()
	remaining := make([]string, 0, len(containers)-1)
	for _, name := range containers {
		if name != containerName {
			remaining = append(remaining, name)
		}
	}
	if err := application.store.Save(remaining); err != nil {
		return err
	}
	if _, err := application.monitor.Remove(containerName); err != nil {
		if restoreErr := application.store.Save(containers); restoreErr != nil {
			return fmt.Errorf("%v; restoring container config also failed: %w", err, restoreErr)
		}
		return err
	}
	return nil
}

func (application *application) respondAfterMutation(response http.ResponseWriter, request *http.Request) {
	if request.FormValue("redirect") == "1" {
		http.Redirect(response, request, "/", http.StatusSeeOther)
		return
	}
	response.WriteHeader(http.StatusNoContent)
}

func methodNotAllowed(response http.ResponseWriter, allowed string) {
	response.Header().Set("Allow", allowed)
	http.Error(response, http.StatusText(http.StatusMethodNotAllowed), http.StatusMethodNotAllowed)
}

func newReportPage(snapshot monitorSnapshot) reportPage {
	page := reportPage{
		StartedAt:      formatTime(snapshot.StartedAt),
		SampleInterval: snapshot.SampleInterval.String(),
		BucketSizeMiB:  snapshot.BucketSize / mebibyte,
		Containers:     make([]containerReport, 0, len(snapshot.Containers)),
	}

	for _, container := range snapshot.Containers {
		report := containerReport{
			Name:         container.Name,
			EscapedName:  url.PathEscape(container.Name),
			StartedAt:    formatTime(container.StartedAt),
			LastSampleAt: formatOptionalTime(container.LastSampleAt),
			LastError:    container.LastError,
			LastErrorAt:  formatOptionalTime(container.LastErrorAt),
			Count:        container.Count,
			HasSamples:   container.Count > 0,
		}
		if report.HasSamples {
			report.CurrentMiB = formatMiB(float64(container.CurrentBytes))
			report.MinimumMiB = formatMiB(float64(container.MinimumBytes))
			report.MaximumMiB = formatMiB(float64(container.MaximumBytes))
			report.AverageMiB = formatMiB(container.AverageBytes)
		}
		for _, bucket := range container.Histogram {
			report.HistogramRows = append(report.HistogramRows, histogramReport{
				Range: fmt.Sprintf(
					"%.0f-<%.0f MiB",
					float64(bucket.StartBytes)/float64(mebibyte),
					float64(bucket.EndBytes)/float64(mebibyte),
				),
				Count:      bucket.Count,
				Percentage: fmt.Sprintf("%.2f%%", bucket.Percentage),
			})
		}
		page.Containers = append(page.Containers, report)
	}
	return page
}

func formatMiB(bytes float64) string {
	return fmt.Sprintf("%.2f MiB", bytes/float64(mebibyte))
}

func formatTime(value time.Time) string {
	return value.UTC().Format(time.RFC3339)
}

func formatOptionalTime(value time.Time) string {
	if value.IsZero() {
		return "-"
	}
	return formatTime(value)
}

var reportTemplate = template.Must(template.New("report").Parse(`<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Container memory statistics</title>
    <style>
        body { font-family: sans-serif; margin: 2rem; color: #202124; background: #f6f8fa; }
        main { max-width: 1000px; margin: 0 auto; }
        form { display: inline-flex; gap: .5rem; align-items: center; }
        input, button { font: inherit; padding: .45rem .65rem; }
        button { cursor: pointer; }
        article { background: white; border: 1px solid #d0d7de; border-radius: 8px; margin: 1.5rem 0; padding: 1.25rem; }
        article header { display: flex; justify-content: space-between; gap: 1rem; align-items: center; }
        h2 { margin: 0; }
        dl { display: grid; grid-template-columns: max-content 1fr; gap: .35rem 1rem; }
        dt { font-weight: 600; }
        dd { margin: 0; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border-bottom: 1px solid #d8dee4; padding: .45rem; text-align: left; }
        .error { color: #b42318; }
        .meta { color: #57606a; }
        @media (max-width: 600px) {
            body { margin: 1rem; }
            article header { align-items: flex-start; flex-direction: column; }
        }
    </style>
</head>
<body>
<main>
    <h1>Container memory statistics</h1>
    <p class="meta">Started {{.StartedAt}} | sample interval {{.SampleInterval}} | histogram buckets {{.BucketSizeMiB}} MiB</p>
    <form method="post" action="/add">
        <input type="hidden" name="redirect" value="1">
        <label for="name">Container</label>
        <input id="name" name="name" required placeholder="container-name">
        <button type="submit">Add</button>
    </form>

    {{if not .Containers}}
    <p>No containers are currently monitored.</p>
    {{end}}

    {{range .Containers}}
    <article>
        <header>
            <h2>{{.Name}}</h2>
            <form method="post" action="/del/{{.EscapedName}}">
                <input type="hidden" name="redirect" value="1">
                <button type="submit">Remove</button>
            </form>
        </header>

        <dl>
            <dt>Observed since</dt><dd>{{.StartedAt}}</dd>
            <dt>Last sample</dt><dd>{{.LastSampleAt}}</dd>
            <dt>Observations</dt><dd>{{.Count}}</dd>
            {{if .HasSamples}}
            <dt>Current</dt><dd>{{.CurrentMiB}}</dd>
            <dt>Minimum</dt><dd>{{.MinimumMiB}}</dd>
            <dt>Maximum</dt><dd>{{.MaximumMiB}}</dd>
            <dt>Average</dt><dd>{{.AverageMiB}}</dd>
            {{end}}
        </dl>

        {{if .LastError}}
        <p class="error">Last error at {{.LastErrorAt}}: {{.LastError}}</p>
        {{end}}

        {{if .HistogramRows}}
        <h3>Histogram</h3>
        <table>
            <thead><tr><th>Memory range</th><th>Observations</th><th>Percentage</th></tr></thead>
            <tbody>
            {{range .HistogramRows}}
            <tr><td>{{.Range}}</td><td>{{.Count}}</td><td>{{.Percentage}}</td></tr>
            {{end}}
            </tbody>
        </table>
        {{end}}
    </article>
    {{end}}
</main>
</body>
</html>`))
