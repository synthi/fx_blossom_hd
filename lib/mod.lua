local fx = require("fx/lib/fx")
local mod = require("core/mods")
local hook = require("core/hook")
local tab = require("tabutil")

-- ========================================================================
-- BLOQUE DE INYECCIÓN FX-MOD (HACK DE INICIALIZACIÓN)
-- Este bloque intercepta el arranque de Norns para inyectar los parámetros
-- de forma segura sin romper el script anfitrión.
-- ========================================================================
if hook.script_post_init == nil and mod.hook.patched == nil then
    mod.hook.patched = true
    local old_register = mod.hook.register
    local post_init_hooks = {}
    mod.hook.register = function(h, name, f)
        if h == "script_post_init" then
            post_init_hooks[name] = f
        else
            old_register(h, name, f)
        end
    end
    mod.hook.register('script_pre_init', '!replace init for fake post init', function()
        local old_init = init
        init = function()
            if old_init then old_init() end -- Nil coalescing obligatorio (Protección contra crashes)
            for i, k in ipairs(tab.sort(post_init_hooks)) do
                local cb = post_init_hooks[k]
                print('calling: ', k)
                local ok, err = pcall(cb)
                if not ok then
                    print('hook: ' .. k .. ' failed, error: ' .. tostring(err))
                end
            end
        end
    end)
end

-- ========================================================================
-- DECLARACIÓN DEL MÓDULO (FASE 1)
-- ========================================================================
local FxBlossom_hd = fx:new{
    subpath = "/fx_blossom_hd"
}

-- ========================================================================
-- ASIGNACIÓN DE PARÁMETROS A LA UI DE NORNS (FASE 2)
-- ========================================================================
function FxBlossom_hd:add_params()
    -- Agrupamos los 7 parámetros bajo un solo menú colapsable para mantener la UI limpia
    params:add_group("fx_blossom_hd", "fx blossom_hd", 7)
    
    -- Slot de ruteo (none, sendA, sendB, insert)
    FxBlossom_hd:add_slot("fx_blossom_hd_slot", "slot")
    
    -- [DECAY]: Tiempo de decaimiento del tanque. 100s actúa como un Freeze infinito.
    -- Curva k=3 (Exponencial) para mayor resolución en tiempos cortos.
    FxBlossom_hd:add_taper("fx_blossom_hd_decay", "decay", "decay", 0.1, 100.0, 3.0, 3, "s")
    
    --[BLOOM]: Tiempo de difusión de los Allpass. Expandido a 4.0s para Ambient extremo.
    -- Valores altos generan "Phase Smearing" (congelación de fase).
    FxBlossom_hd:add_taper("fx_blossom_hd_bloom", "bloom", "bloom", 0.01, 4.0, 0.5, 0, "s")
    
    -- [DAMP]: Filtro pasa-bajos global. Rango ajustado de 150Hz a 15000Hz.
    -- Curva k=4 para que el encoder tenga precisión micrométrica en la zona de "calidez" (400Hz-4kHz).
    FxBlossom_hd:add_taper("fx_blossom_hd_damp", "damp", "damp", 150, 15000, 10000, 4, "hz")
    
    -- [PREDELAY]: Retraso inicial. El canal R tiene un offset oculto de +11.27ms (Haas Onset).
    FxBlossom_hd:add_taper("fx_blossom_hd_predelay", "predelay", "predelay", 0.0, 1.0, 0.0, 0, "s")
    
    -- [MOD RATE]: Velocidad base de los 20 LFOs. Internamente se multiplica por fracciones coprimas.
    FxBlossom_hd:add_taper("fx_blossom_hd_mod_rate", "mod rate", "mod_rate", 0.0, 10.0, 0.5, 0, "hz")
    
    -- [MOD DEPTH]: Profundidad del Chorusing interno. Valores altos desafinan la cola (Pitch Wobble).
    FxBlossom_hd:add_taper("fx_blossom_hd_mod_depth", "mod depth", "mod_depth", 0.0, 0.01, 0.001, 0, "s")
end

-- Registro de los parámetros en el ciclo de vida del script anfitrión
mod.hook.register("script_post_init", "fx blossom_hd mod post init", function()
    FxBlossom_hd:add_params()
end)

mod.hook.register("script_post_cleanup", "blossom_hd mod post cleanup", function()
    -- Reservado para recolección de basura si fuera necesario en el futuro
end)

return FxBlossom_hd
