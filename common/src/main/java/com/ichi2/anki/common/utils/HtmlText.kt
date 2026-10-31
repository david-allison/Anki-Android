// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>

package com.ichi2.anki.common.utils

import java.util.regex.Pattern

private val commentPattern = Pattern.compile("(?s)<!--.*?-->")
private val stylePattern = Pattern.compile("(?si)<style.*?>.*?</style>")
private val scriptPattern = Pattern.compile("(?si)<script.*?>.*?</script>")
private val tagPattern = Pattern.compile("(?s)<.*?>")
private val typePattern = Pattern.compile("(?s)\\[\\[type:.+?]]")
private val avRefPattern = Pattern.compile("(?s)\\[anki:play:.:\\d+?]")

/** Strip `[[type:...]]` and `[anki:play...]` references from card text. */
fun stripSpecialFields(input: String): String = avRefPattern.matcher(typePattern.matcher(input).replaceAll("")).replaceAll("")

/** Strip comments, script/style content and HTML tags, preserving escaped entities. */
fun stripHtmlTags(input: String): String =
    tagPattern.matcher(stripHTMLScriptAndStyleTags(commentPattern.matcher(input).replaceAll(""))).replaceAll("")

/** Strip script/style elements and their content, preserving all other markup. */
fun stripHTMLScriptAndStyleTags(input: String): String = scriptPattern.matcher(stylePattern.matcher(input).replaceAll("")).replaceAll("")

/** Prepare deck description markup for rendering without displaying scripts or losing newlines. */
fun prepareDeckDescription(input: String): String = stripHTMLScriptAndStyleTags(input).replace("\r\n", "<br/>").replace("\n", "<br/>")
