package de.qabel.client.box

import de.qabel.client.box.interactor.BrowserEntry
import de.qabel.client.box.interactor.DownloadSource
import java.io.ByteArrayInputStream

fun ByteArray.toDownloadSource(entry: BrowserEntry.File)
    = DownloadSource(entry, ByteArrayInputStream(this))

