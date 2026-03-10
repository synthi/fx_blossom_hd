FxBlossom_hd : FxBase {

    *new { 
        var ret = super.newCopyArgs(nil, \none, (
            decay: 3.0,
            bloom: 0.5,
            damp: 10000,
            predelay: 0.0,
            mod_rate: 0.5,
            mod_depth: 0.001
        ), nil, 1.0);
        ^ret;
    }

    *initClass {
        FxSetup.register(this.new);
    }

    subPath {
        ^"/fx_blossom_hd";
    }  

    symbol {
        ^\fxBlossom_hd;
    }

    addSynthdefs {
        SynthDef(\fxBlossom_hd, { |inBus, outBus|
            // ========================================================================
            // FASE 1: DECLARACIÓN ABSOLUTA DE VARIABLES (DIRECTIVA DE SINTAXIS ESTRICTA)
            // ========================================================================
            var input, predelay_l, predelay_r;
            var combs_l, combs_r;
            var cross_l, cross_r;
            var ap_l, ap_r;
            var rev_filt_l, rev_filt_r;
            var rev_out_l, rev_out_r;
            
            // Matrices de Tiempos Primos (Convertidos de muestras a segundos a 48kHz)
            var prime_combs_l, prime_combs_r;
            var prime_ap_l, prime_ap_r;
            
            // Matrices de Modulación (Regla de Golomb para LFOs)
            var lfo_rates_tank_l, lfo_rates_tank_r, lfo_rates_ap_l, lfo_rates_ap_r;
            var lfo_phases_tank_l, lfo_phases_tank_r, lfo_phases_ap_l, lfo_phases_ap_r;
            
            // Variables de Control (Rate .kr)
            var decay_kr, bloom_kr, damp_kr, predelay_kr, mod_rate_kr, mod_depth_kr;

            // ========================================================================
            // FASE 2: ASIGNACIÓN Y OPERACIÓN DSP
            // ========================================================================
            
            // --- BLOQUE A: MATRIZ MATEMÁTICA (NO MODIFICAR SIN RECALCULAR PRIMOS) ---
            // Tiempos del Tanque (14 Comb Filters). Rango: ~21ms a ~95ms.
            prime_combs_l =[0.021020, 0.030979, 0.041520, 0.052895, 0.064229, 0.076479, 0.089229];
            prime_combs_r =[0.025479, 0.036104, 0.047229, 0.058354, 0.070229, 0.083395, 0.095895];
            
            // Tiempos de Difusión (6 Allpass Filters). Rango: ~4ms a ~17ms.
            prime_ap_l =[0.004645, 0.009145, 0.014604];
            prime_ap_r =[0.006479, 0.011729, 0.017770];

            // Multiplicadores Fraccionarios Coprimos para los 20 LFOs (Erradica el Beating cíclico)
            lfo_rates_tank_l =[0.43, 0.47, 0.53, 0.59, 0.61, 0.67, 0.71];
            lfo_rates_tank_r =[0.73, 0.79, 0.83, 0.89, 0.97, 1.01, 1.03];
            lfo_rates_ap_l =[1.07, 1.09, 1.13];
            lfo_rates_ap_r = [1.27, 1.31, 1.37];

            // Fases Iniciales Estáticas (0 a 2pi). Evita el "Pitch Dive" al instanciar el efecto.
            lfo_phases_tank_l =[0.0, 0.73, 1.27, 2.11, 2.83, 3.17, 3.89];
            lfo_phases_tank_r =[4.57, 5.21, 5.93, 0.41, 1.09, 1.79, 2.51];
            lfo_phases_ap_l =[3.47, 4.01, 4.87];
            lfo_phases_ap_r = [5.41, 6.01, 0.13];

            // --- BLOQUE B: LECTURA DE CONTROL Y AUDIO ---
            // Lag de 0.1s obligatorio para evitar Zipper Noise al mover encoders en Norns
            decay_kr = \decay.kr(3.0).lag(0.1);
            bloom_kr = \bloom.kr(0.5).lag(0.1);
            damp_kr = \damp.kr(10000).lag(0.1);
            predelay_kr = \predelay.kr(0.0).lag(0.1);
            mod_rate_kr = \mod_rate.kr(0.5).lag(0.1);
            mod_depth_kr = \mod_depth.kr(0.001).lag(0.1);

            // Lectura segura del bus global usando InFeedback para evitar Node Order Errors
            input = InFeedback.ar(inBus, 2);

            // --- BLOQUE C: PREDELAY ASIMÉTRICO (HAAS ONSET) ---
            // El canal R tiene un offset fijo de 541 muestras (11.27ms).
            // Esto vacía el centro estéreo y abraza al oyente inmediatamente.
            // Se usa DelayC (Cúbico) para permitir manipulación en vivo (Efecto Doppler).
            predelay_l = DelayC.ar(input[0], 1.5, predelay_kr) * 0.1; // attenuation
            predelay_r = DelayC.ar(input[1], 1.5, predelay_kr + 0.0112708) * 0.1; 

            // --- BLOQUE D: EL TANQUE (14 COMB FILTERS MODULADOS) ---
            // Generación de densidad modal masiva. Cada Comb tiene su propio LFO independiente.
            combs_l = prime_combs_l.collect { |time, i|
                var local_time = time; // Protección de scope
                var local_i = i;       // Protección de scope
                var lfo = SinOsc.kr(mod_rate_kr * lfo_rates_tank_l[local_i], lfo_phases_tank_l[local_i]) * mod_depth_kr;
                CombC.ar(predelay_l, 0.2, local_time + lfo, decay_kr);
            }.sum;

            combs_r = prime_combs_r.collect { |time, i|
                var local_time = time;
                var local_i = i;
                var lfo = SinOsc.kr(mod_rate_kr * lfo_rates_tank_r[local_i], lfo_phases_tank_r[local_i]) * mod_depth_kr;
                CombC.ar(predelay_r, 0.2, local_time + lfo, decay_kr);
            }.sum;

            // --- BLOQUE E: CROSS-POLLINATION (INYECCIÓN CRUZADA) ---
            // Mezcla un 20% de la energía del tanque opuesto para crear cohesión 3D.
            cross_l = combs_l + (combs_r * 0.2);
            cross_r = combs_r + (combs_l * 0.2);
            // NUEVO: Baffle Acústico (Filtro Pasa-Altos suave a 60Hz)
            // Limpia la masa de graves del tanque antes de que entre a la etapa de difusión (Bloom)
            cross_l = HPF.ar(cross_l, 60);
            cross_r = HPF.ar(cross_r, 60);
            

            // --- BLOQUE F: DIFUSIÓN PROFUNDA (BLOOM) ---
            // 6 Allpass Filters en serie con interpolación cúbica (AllpassC).
            // Transforma los ecos del tanque en una nube de plasma acústico (Smearing).
            ap_l = cross_l;
            ap_r = cross_r;
            
            3.do { |i|
                var local_i = i;
                var lfo_l = SinOsc.kr(mod_rate_kr * lfo_rates_ap_l[local_i], lfo_phases_ap_l[local_i]) * mod_depth_kr;
                var lfo_r = SinOsc.kr(mod_rate_kr * lfo_rates_ap_r[local_i], lfo_phases_ap_r[local_i]) * mod_depth_kr;
                
                ap_l = AllpassC.ar(ap_l, 0.05, prime_ap_l[local_i] + lfo_l, bloom_kr);
                ap_r = AllpassC.ar(ap_r, 0.05, prime_ap_r[local_i] + lfo_r, bloom_kr);
            };

            // --- BLOQUE G: DAMPING GLOBAL (TECHO ACÚSTICO) ---
            // Simula la absorción de altas frecuencias por el aire y las paredes.
            rev_filt_l = LPF.ar(ap_l, damp_kr);
            rev_filt_r = LPF.ar(ap_r, damp_kr);

            // --- BLOQUE H: ESTABILIZACIÓN DC Y SATURACIÓN (EQUAL GAIN) ---
            // 1. LeakDC: Centra la onda para evitar asimetrías en tiempos de decay infinitos.
            // 2. * 0.3: Pad de atenuación para que el tanque no ahogue el saturador.
            // 3. .tanh: Saturación analógica suave (Soft-Clipper).
            // 4. * 3.333: Makeup Gain exacto para restaurar el volumen (Equal Gain).
            // 5. .softclip: Muro de contención final para proteger los conversores D/A.
            rev_out_l = ((LeakDC.ar(rev_filt_l) * 0.05).tanh * 3.6).softclip;
            rev_out_r = ((LeakDC.ar(rev_filt_r) * 0.05).tanh * 3.6).softclip;

            Out.ar(outBus,[rev_out_l, rev_out_r]);
        }).add;
    }

}
