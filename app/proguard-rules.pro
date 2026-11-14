# WorkManager's default rules retain worker constructors.
-keep class dev.chanflow.app.DownloadWorker { public <init>(...); }
