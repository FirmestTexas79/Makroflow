package cz.uhk.macroflow.pokemon

/**
 * 🌀 Společné rozhraní pro jakýkoliv pohyb Pokémona na liště.
 */
interface PokemonBehavior {
    fun start()
    fun stop()
    fun onSpriteClicked()
}