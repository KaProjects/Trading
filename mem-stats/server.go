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
	history historyReader
	mu      sync.Mutex
}

type reportPage struct {
	StartedAt          string
	SampleInterval     string
	CheckpointInterval string
	BucketSizeMiB      uint64
	Containers         []containerReport
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
	Value      float64
}

type historyPage struct {
	Records []historyReport
}

type historyReport struct {
	ID                 string
	Name               string
	ContainerID        string
	ContainerStartedAt string
	ObservedFrom       string
	ObservedUntil      string
	LastSampleAt       string
	Reason             string
	Count              uint64
	HasSamples         bool
	CurrentMiB         string
	MinimumMiB         string
	MaximumMiB         string
	AverageMiB         string
	HistogramRows      []histogramReport
}

func newApplication(monitor *monitor, store *configStore, history historyReader) *application {
	return &application{monitor: monitor, store: store, history: history}
}

func (application *application) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", application.handleReport)
	mux.HandleFunc("/history", application.handleHistory)
	mux.HandleFunc("/health", application.handleHealth)
	mux.HandleFunc("/add", application.handleAdd)
	mux.HandleFunc("/add/", application.handleAdd)
	mux.HandleFunc("/del/", application.handleDelete)
	return mux
}

func (application *application) handleHistory(response http.ResponseWriter, request *http.Request) {
	if request.Method != http.MethodGet {
		methodNotAllowed(response, http.MethodGet)
		return
	}

	records, err := application.history.ListFinalized()
	if err != nil {
		http.Error(response, "load history", http.StatusInternalServerError)
		return
	}

	response.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := historyTemplate.Execute(response, newHistoryPage(records)); err != nil {
		http.Error(response, "render history", http.StatusInternalServerError)
	}
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

	if err := application.addContainer(request.Context(), containerName); err != nil {
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

func (application *application) addContainer(ctx context.Context, containerName string) error {
	if err := validateContainerName(containerName); err != nil {
		return err
	}

	if application.monitor.Has(containerName) {
		return nil
	}
	if err := application.monitor.ValidateRunning(ctx, containerName); err != nil {
		return fmt.Errorf("cannot monitor %q: %w", containerName, err)
	}

	application.mu.Lock()
	defer application.mu.Unlock()

	if application.monitor.Has(containerName) {
		return nil
	}

	existingContainers := application.monitor.Names()
	containers := append(existingContainers, containerName)
	if err := application.store.Save(containers); err != nil {
		return err
	}
	if _, err := application.monitor.Add(containerName, nil); err != nil {
		if restoreErr := application.store.Save(existingContainers); restoreErr != nil {
			return fmt.Errorf("%v; restoring container config also failed: %w", err, restoreErr)
		}
		return err
	}

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
		StartedAt:          formatTime(snapshot.StartedAt),
		SampleInterval:     snapshot.SampleInterval.String(),
		CheckpointInterval: snapshot.CheckpointInterval.String(),
		BucketSizeMiB:      snapshot.BucketSize / mebibyte,
		Containers:         make([]containerReport, 0, len(snapshot.Containers)),
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
		report.HistogramRows = newHistogramReport(container.Histogram)
		page.Containers = append(page.Containers, report)
	}
	return page
}

func newHistoryPage(records []historyRecord) historyPage {
	page := historyPage{Records: make([]historyReport, 0, len(records))}
	for _, record := range records {
		report := historyReport{
			ID:                 record.ID,
			Name:               record.ContainerName,
			ContainerID:        optionalText(record.ContainerID),
			ContainerStartedAt: optionalText(record.ContainerStartedAt),
			ObservedFrom:       formatTime(record.ObservedFrom),
			ObservedUntil:      formatTime(record.ObservedUntil),
			LastSampleAt:       formatOptionalTime(record.LastSampleAt),
			Reason:             record.Reason,
			Count:              record.Count,
			HasSamples:         record.Count > 0,
			HistogramRows:      newHistogramReport(record.Histogram),
		}
		if report.HasSamples {
			report.CurrentMiB = formatMiB(float64(record.CurrentBytes))
			report.MinimumMiB = formatMiB(float64(record.MinimumBytes))
			report.MaximumMiB = formatMiB(float64(record.MaximumBytes))
			report.AverageMiB = formatMiB(record.AverageBytes)
		}
		page.Records = append(page.Records, report)
	}
	return page
}

func newHistogramReport(histogram []histogramBucket) []histogramReport {
	rows := make([]histogramReport, 0, len(histogram))
	for _, bucket := range histogram {
		rows = append(rows, histogramReport{
			Range: fmt.Sprintf(
				"%.0f-<%.0f MiB",
				float64(bucket.StartBytes)/float64(mebibyte),
				float64(bucket.EndBytes)/float64(mebibyte),
			),
			Count:      bucket.Count,
			Percentage: fmt.Sprintf("%.2f%%", bucket.Percentage),
			Value:      bucket.Percentage,
		})
	}
	return rows
}

func optionalText(value string) string {
	if value == "" {
		return "-"
	}
	return value
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
        .tabs { display: flex; gap: .35rem; margin: 0 0 1.25rem; border-bottom: 1px solid #d0d7de; }
        .tab { color: #57606a; padding: .55rem .85rem; text-decoration: none; border-bottom: 3px solid transparent; }
        .tab.active { color: #0969da; border-color: #0969da; font-weight: 700; }
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
    <nav class="tabs" aria-label="Statistics views">
        <a class="tab active" href="/" aria-current="page">Current</a>
        <a class="tab" href="/history">History</a>
    </nav>
    <p class="meta">Started {{.StartedAt}} | sample interval {{.SampleInterval}} | checkpoint interval {{.CheckpointInterval}} | histogram buckets {{.BucketSizeMiB}} MiB</p>
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

var historyTemplate = template.Must(template.New("history").Parse(`<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Container memory history</title>
    <style>
        body { font-family: sans-serif; margin: 2rem; color: #202124; background: #f6f8fa; }
        main { max-width: 1000px; margin: 0 auto; }
        .tabs { display: flex; gap: .35rem; margin: 0 0 1.25rem; border-bottom: 1px solid #d0d7de; }
        .tab { color: #57606a; padding: .55rem .85rem; text-decoration: none; border-bottom: 3px solid transparent; }
        .tab.active { color: #0969da; border-color: #0969da; font-weight: 700; }
        .meta { color: #57606a; }
        .history-record { background: white; border: 1px solid #d0d7de; border-radius: 8px; margin: .7rem 0; padding: .85rem 1rem; }
        .history-record header { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
        h2 { font-size: 1.05rem; margin: 0 0 .2rem; }
        .window { color: #57606a; font-size: .85rem; margin: 0; }
        .reason { background: #ddf4ff; border-radius: 999px; color: #0550ae; font-size: .75rem; padding: .25rem .55rem; white-space: nowrap; }
        .stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: .5rem; margin-top: .8rem; }
        .stat { background: #f6f8fa; border-radius: 6px; padding: .45rem .55rem; }
        .stat-label { color: #57606a; display: block; font-size: .7rem; text-transform: uppercase; }
        .stat-value { display: block; font-size: .9rem; font-weight: 700; margin-top: .1rem; }
        details { margin-top: .65rem; }
        summary { color: #0969da; cursor: pointer; font-size: .85rem; }
        dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: .3rem .8rem; font-size: .8rem; margin: .7rem 0; }
        dt { color: #57606a; font-weight: 600; }
        dd { margin: 0; overflow-wrap: anywhere; }
        .histogram { border-top: 1px solid #d8dee4; margin-top: .65rem; padding-top: .65rem; }
        .histogram h3 { font-size: .85rem; margin: 0 0 .5rem; }
        .histogram-row { align-items: center; display: grid; font-size: .75rem; gap: .5rem; grid-template-columns: 8rem minmax(5rem, 1fr) 3rem 4rem; margin: .3rem 0; }
        progress { accent-color: #218bff; width: 100%; }
        @media (max-width: 600px) {
            body { margin: 1rem; }
            .history-record header { flex-direction: column; gap: .5rem; }
            .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
            .histogram-row { grid-template-columns: 7rem minmax(4rem, 1fr) 3rem; }
            .histogram-row .count { display: none; }
        }
    </style>
</head>
<body>
<main>
    <h1>Container memory statistics</h1>
    <nav class="tabs" aria-label="Statistics views">
        <a class="tab" href="/">Current</a>
        <a class="tab active" href="/history" aria-current="page">History</a>
    </nav>

    {{if not .Records}}
    <p class="meta">No completed monitoring runs have been recorded yet.</p>
    {{end}}

    {{range .Records}}
    <article class="history-record">
        <header>
            <div>
                <h2>{{.Name}}</h2>
                <p class="window">{{.ObservedFrom}} to {{.ObservedUntil}}</p>
            </div>
            <span class="reason">{{.Reason}}</span>
        </header>

        <div class="stats">
            <div class="stat"><span class="stat-label">Samples</span><span class="stat-value">{{.Count}}</span></div>
            {{if .HasSamples}}
            <div class="stat"><span class="stat-label">Minimum</span><span class="stat-value">{{.MinimumMiB}}</span></div>
            <div class="stat"><span class="stat-label">Average</span><span class="stat-value">{{.AverageMiB}}</span></div>
            <div class="stat"><span class="stat-label">Maximum</span><span class="stat-value">{{.MaximumMiB}}</span></div>
            <div class="stat"><span class="stat-label">Last</span><span class="stat-value">{{.CurrentMiB}}</span></div>
            {{end}}
        </div>

        <details>
            <summary>Run details and histogram</summary>
            <dl>
                <dt>Record ID</dt><dd>{{.ID}}</dd>
                <dt>Container ID</dt><dd>{{.ContainerID}}</dd>
                <dt>Container started</dt><dd>{{.ContainerStartedAt}}</dd>
                <dt>Last sample</dt><dd>{{.LastSampleAt}}</dd>
            </dl>
            {{if .HistogramRows}}
            <section class="histogram">
                <h3>Histogram</h3>
                {{range .HistogramRows}}
                <div class="histogram-row">
                    <span>{{.Range}}</span>
                    <progress max="100" value="{{.Value}}">{{.Percentage}}</progress>
                    <span>{{.Percentage}}</span>
                    <span class="count">{{.Count}} samples</span>
                </div>
                {{end}}
            </section>
            {{end}}
        </details>
    </article>
    {{end}}
</main>
</body>
</html>`))
