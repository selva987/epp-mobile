package org.ugd.epp;

import android.graphics.Rect;

import org.pytorch.demo.objectdetection.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringJoiner;

public class AlgoritmoDecision {

    private ArrayList<Result> resultadosEntrada;
    private ArrayList<EPPResult> resultadosProcesados;
    private boolean modoPortal;

    private HashMap<String, Integer> clases;

    public AlgoritmoDecision(ArrayList<Result> results, boolean modoPortal) {
        this.resultadosEntrada = results;
        this.modoPortal = modoPortal;
        this.inicializarClases();
    }


    public ArrayList<EPPResult> getResultadosProcesados() {
        if(this.resultadosProcesados == null) {
            this.procesarResultados();
        }

        return this.resultadosProcesados;
    }

    private void inicializarClases() {
        this.clases = new HashMap<>();
        this.clases.put("cabeza",   0);
        this.clases.put("casco",    1);
        this.clases.put("chaleco",  2);
        this.clases.put("guantes",  3);
        this.clases.put("manos",    4);
        this.clases.put("persona",  5);
    }

    private void procesarResultados() {
        //Inicializar los resultados procesados
        this.resultadosProcesados = new ArrayList<>();

        // Primero se buscan las persona detectadas
        ArrayList<Result> personas = this.getResultadosByClase("persona");
        //epp es una variable auxiliar que va a contener los epp buscados
        ArrayList<Result> epp;
        //Contiene la leyenda de los elementos faltantes
        StringJoiner faltas;
        boolean ok;
        EPPResult result;

        for(Result persona : personas) {
            faltas = new StringJoiner(" ");

            /////Deteccion de casco
            //Se busca si la persona tiene casco
            epp = this.getResultadosByCoordenadas(persona.getRect(), "casco");
            if(epp.isEmpty()) {
                //Si no tiene casco, se busca si se encuentra la cabeza
                epp = this.getResultadosByCoordenadas(persona.getRect(), "cabeza");
                //Si se encuentra una cabeza, se determina que falta el casco
                if(!epp.isEmpty()) {
                    faltas.add("CA");
                }
            }

            //////Deteccion de chaleco
            //Se busca si la persona tiene chaleco
            epp = this.getResultadosByCoordenadas(persona.getRect(), "chaleco");
            if(epp.isEmpty()) {
                //Si no se encuentra el chaleco, se determina que falta
                faltas.add("CH");
            }

            ///////Deteccion de guantes
            //Se busca si se pueden ver las manos de la persona
            epp = this.getResultadosByCoordenadas(persona.getRect(), "manos");
            if(!epp.isEmpty()) {
                //Si se encuentran manos, se determina que faltan guantes
                faltas.add("GU");
            } else if(this.modoPortal) {
                //Si no se encuentran manos y el modo portal esta activo, se debe buscar guantes
                epp = this.getResultadosByCoordenadas(persona.getRect(), "guantes");
                if(epp.size() < 2) {
                    //Si no se encuentran al menos dos guantes, se determina que faltan guantes
                    faltas.add(("GU"));
                }
            }

            //Una vez determinado el resultado, se agrega a la lista
            if(faltas.length() > 0) {
                this.resultadosProcesados.add(new EPPResult(persona, faltas.toString(), false));
            } else {
                this.resultadosProcesados.add(new EPPResult(persona, "Ok", true));
            }

        }

    }

    private ArrayList<Result> getResultadosByClase(String clase) {
        ArrayList<Result> lista = new ArrayList<>();

        int id = this.clases.get(clase);

        for(Result r : this.resultadosEntrada) {
            if(r.getClassIndex() == id) {
                lista.add(r);
            }
        }

        return lista;
    }


    private ArrayList<Result> getResultadosByCoordenadas(Rect coordenadas, String clase) {
        int id = this.clases.get(clase);
        ArrayList<Result> lista = new ArrayList<>();

        for(Result r : this.resultadosEntrada) {
            if(id == r.getClassIndex() && Rect.intersects(coordenadas, r.getRect())) {
                lista.add(r);
            }
        }

        return lista;
    }

}
