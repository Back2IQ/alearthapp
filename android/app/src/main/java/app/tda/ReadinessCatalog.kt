package app.tda

/** Eine Ausrüstungsposition (spec TP-5 §C). i18n: prep_item_<key> = Name, prep_why_<key> = Nutzen. */
data class PrepItem(val key: String, val query: String, val group: Group)

enum class Group { BASE, QUAKE, PET, KIDS, CAR }

/**
 * Katalog der Vorsorge-Positionen (Kotlin-Port + Erweiterung von webapp/lib/prep.js). Reine Daten.
 * BASE + QUAKE gelten immer; PET/KIDS/CAR nur bei gesetztem Profil-Schalter.
 */
object ReadinessCatalog {
    val ITEMS: List<PrepItem> = listOf(
        PrepItem("water", "Trinkwasser Notvorrat", Group.BASE),
        PrepItem("firstaid", "Erste-Hilfe-Set", Group.BASE),
        PrepItem("gobag", "Notfallrucksack Fluchtrucksack", Group.BASE),
        PrepItem("docs", "wasserdichte Dokumententasche", Group.BASE),
        PrepItem("radio", "Kurbelradio Notfallradio", Group.BASE),
        PrepItem("powerbank", "Powerbank", Group.BASE),
        PrepItem("whistle", "Trillerpfeife Notsignal", Group.QUAKE),
        PrepItem("helmet", "Schutzhelm", Group.QUAKE),
        PrepItem("mask", "FFP2 Staubmaske", Group.QUAKE),
        PrepItem("blanket", "Rettungsdecke", Group.QUAKE),
        PrepItem("petcarrier", "Transportbox Haustier", Group.PET),
        PrepItem("petfood", "Tierfutter Vorrat", Group.PET),
        PrepItem("kidsfirstaid", "Kinder Erste-Hilfe-Set", Group.KIDS),
        PrepItem("kidssupplies", "Windeln Babybedarf", Group.KIDS),
        PrepItem("jumpstarter", "Starthilfe Powerbank Auto", Group.CAR),
        PrepItem("warnvest", "Warnweste", Group.CAR),
        PrepItem("towrope", "Abschleppseil", Group.CAR),
    )

    fun applicable(hasPet: Boolean, hasKids: Boolean, hasCar: Boolean): List<PrepItem> =
        ITEMS.filter {
            when (it.group) {
                Group.BASE, Group.QUAKE -> true
                Group.PET -> hasPet
                Group.KIDS -> hasKids
                Group.CAR -> hasCar
            }
        }
}
